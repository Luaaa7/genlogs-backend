package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.SolicitudCreadaResponse;
import com.genlogs.app.dto.SolicitudRequest;
import com.genlogs.app.enums.CanalSolicitud;
import com.genlogs.app.enums.EstadoSolicitud;
import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.enums.TipoPlazo;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MarcaRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Endpoint único de solicitudes (BE-B). Portal, landing y vitrina crean el pedido aquí.
 *
 * Reglas D-01 por canal:
 *   - Con N.º de parte, siempre se pide la marca (catálogo o texto).
 *   - PORTAL: sin N.º de parte exige foto o plano.
 *   - LANDING: basta la descripción; el asistente pide la foto después.
 *   - VITRINA: un ítem con idProducto debe ser un producto visible de la vitrina; los
 *     ítems sin idProducto (p. ej. "Volver a pedir" en el mismo carrito) siguen la regla del PORTAL.
 * El plazo interno (RF-84) es ESPECIAL si algún repuesto no está identificado (sin N.º de
 * parte ni producto de la vitrina) y COMUN en otro caso; el asistente lo reclasifica a
 * IMPORTADO si corresponde.
 */
@Service
@RequiredArgsConstructor
public class SolicitudService {

    static final long TAMANO_MAXIMO_FOTO = 10L * 1024L * 1024L;
    private static final String CARPETA_FOTOS = "genlogs/solicitudes";
    private static final Set<String> TIPOS_IMAGEN = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String TIPO_PDF = "application/pdf";
    private static final int MAX_URL_FOTO = 2048;

    private final SolicitudCotizacionRepository solicitudRepository;
    private final BitacoraSolicitud bitacora;
    private final UsuarioRepository usuarioRepository;
    private final MarcaRepository marcaRepository;
    private final ProductoRepository productoRepository;
    private final ParametrosService parametrosService;
    private final CalendarioHabil calendarioHabil;
    private final LimitadorSolicitudes limitador;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public SolicitudCreadaResponse crear(SolicitudRequest request) {
        Usuario usuario = exigirCliente();
        limitador.verificar(usuario.getIdUsuario());

        boolean algunoSinNumeroParte = false;
        for (SolicitudRequest.Item item : request.getItems()) {
            validarItem(request.getCanal(), item);
            algunoSinNumeroParte |= vacio(item.getNumeroParte()) && item.getIdProducto() == null;
        }

        LocalDateTime ahora = LocalDateTime.now();
        TipoPlazo tipoPlazo = algunoSinNumeroParte ? TipoPlazo.ESPECIAL : TipoPlazo.COMUN;
        String proceso = "SOLICITUD_" + request.getCanal().name();

        SolicitudCotizacion solicitud = new SolicitudCotizacion();
        solicitud.setCliente(usuario.getCliente());
        solicitud.setUsuario(usuario);
        solicitud.setCodigoSolicitud(nuevoCodigo());
        solicitud.setAsunto(request.getAsunto().trim());
        solicitud.setDescripcion(vacio(request.getDescripcion())
                ? request.getAsunto().trim()
                : request.getDescripcion().trim());
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE.name());
        solicitud.setTipoPlazo(tipoPlazo.name());
        solicitud.setFechaLimiteInterna(calendarioHabil.fechaLimite(ahora, diasDePlazo(tipoPlazo)));
        auditar(solicitud, usuario, proceso, ahora);

        for (SolicitudRequest.Item item : request.getItems()) {
            SolicitudItem linea = new SolicitudItem();
            linea.setDescripcion(item.getDescripcion().trim());
            linea.setCantidad(item.getCantidad());
            linea.setUnidad(textoOpcional(item.getUnidad()));
            linea.setIdMarca(item.getIdMarca());
            linea.setMarcaTexto(textoOpcional(item.getMarcaTexto()));
            linea.setNumeroParte(textoOpcional(item.getNumeroParte()));
            linea.setGtin(textoOpcional(item.getGtin()));
            linea.setUrlFoto(textoOpcional(item.getUrlFoto()));
            linea.setIdProducto(item.getIdProducto());
            auditar(linea, usuario, proceso, ahora);
            solicitud.agregarItem(linea);
        }
        SolicitudCotizacion guardada = solicitudRepository.save(solicitud);

        bitacora.registrar(guardada.getIdSolicitud(), usuario.getIdUsuario(), TipoEventoSolicitud.CREADA,
                "Canal " + request.getCanal().name() + ", plazo " + tipoPlazo.name()
                        + ", " + request.getItems().size() + " repuesto(s)");

        return new SolicitudCreadaResponse(guardada.getIdSolicitud(), guardada.getCodigoSolicitud(),
                EstadoSolicitud.PENDIENTE);
    }

    /** Sube la foto o el plano de un repuesto y devuelve la URL para mandarla en urlFoto. */
    public String subirFoto(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Adjunta una foto o un plano.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_FOTO) {
            throw new BusinessException("La foto o el plano no puede pesar más de 10 MB.");
        }
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        if (TIPOS_IMAGEN.contains(tipo)) {
            return cloudinaryService.subirImagen(archivo, CARPETA_FOTOS);
        }
        if (TIPO_PDF.equals(tipo)) {
            return cloudinaryService.subirDocumento(archivo, CARPETA_FOTOS);
        }
        throw new BusinessException("Solo se aceptan fotos JPG, PNG o WEBP, o planos en PDF.");
    }

    private void validarItem(CanalSolicitud canal, SolicitudRequest.Item item) {
        String repuesto = "\"" + item.getDescripcion().trim() + "\"";
        boolean conNumeroParte = !vacio(item.getNumeroParte());

        if (conNumeroParte && item.getIdMarca() == null && vacio(item.getMarcaTexto())) {
            throw new BusinessException("Indica la marca de " + repuesto + ": con N.º de parte la marca es obligatoria.");
        }
        if (item.getIdMarca() != null && !marcaRepository.existsById(item.getIdMarca())) {
            throw new BusinessException("La marca elegida para " + repuesto + " no existe.");
        }
        if (!vacio(item.getUrlFoto())) {
            String url = item.getUrlFoto().trim();
            if (!url.toLowerCase(Locale.ROOT).startsWith("https://") || url.length() > MAX_URL_FOTO) {
                throw new BusinessException("La foto de " + repuesto + " no es válida. Vuelve a subirla.");
            }
        }
        // Un producto de la vitrina ya está identificado. Sin él, PORTAL y VITRINA piden N.º de parte o foto;
        // LANDING acepta solo la descripción (el asistente pide la foto después).
        boolean identificadoPorProducto = item.getIdProducto() != null;
        boolean pideFoto = canal != CanalSolicitud.LANDING && !identificadoPorProducto;
        if (pideFoto && !conNumeroParte && vacio(item.getUrlFoto())) {
            throw new BusinessException("Sin N.º de parte, adjunta una foto o plano de " + repuesto + ".");
        }
        if (item.getIdProducto() != null) {
            boolean enVitrina = productoRepository.findById(item.getIdProducto())
                    .filter(p -> p.getStatus() != null && "A".equals(p.getStatus().trim()) && Boolean.TRUE.equals(p.getVisibleWeb()))
                    .isPresent();
            if (!enVitrina) {
                throw new BusinessException("El producto " + repuesto + " no está disponible en la vitrina.");
            }
        }
    }

    /** Días hábiles del parámetro; si falta la fila se usa el valor de v3_2.sql para no frenar el pedido. */
    private int diasDePlazo(TipoPlazo tipo) {
        String clave = switch (tipo) {
            case COMUN -> ClavesParametro.PLAZO_COMUN_DIAS;
            case IMPORTADO -> ClavesParametro.PLAZO_IMPORTADO_DIAS;
            case ESPECIAL -> ClavesParametro.PLAZO_ESPECIAL_DIAS;
        };
        int porDefecto = switch (tipo) {
            case COMUN -> 1;
            case IMPORTADO -> 3;
            case ESPECIAL -> 5;
        };
        return parametrosService.buscar(clave).map(valor -> {
            try {
                int dias = Integer.parseInt(valor);
                return dias >= 1 ? dias : porDefecto;
            } catch (NumberFormatException e) {
                return porDefecto;
            }
        }).orElse(porDefecto);
    }

    /**
     * Solo un usuario CLIENTE activo con id_cliente. El personal interno recibe 403
     * hasta que el contrato tenga a qué cliente cargar el pedido.
     */
    private Usuario exigirCliente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Sin sesión");
        }
        Usuario usuario = usuarioRepository.findWithClienteByNombreUsuario(auth.getName()).orElse(null);
        if (usuario == null || usuario.getCliente() == null) {
            throw new AccessDeniedException("Solo un cliente del portal puede crear solicitudes por esta ruta");
        }
        if (usuario.getStatus() == null || !usuario.getStatus().trim().startsWith("A")) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        return usuario;
    }

    private String nuevoCodigo() {
        String prefijo = "SOL-" + LocalDate.now().getYear() + "-";
        for (int i = 0; i < 8; i++) {
            String codigo = prefijo + (1 + ThreadLocalRandom.current().nextLong(1_000_000_000L));
            if (codigo.length() <= 20 && !solicitudRepository.existsByCodigoSolicitud(codigo)) {
                return codigo;
            }
        }
        return prefijo + (System.nanoTime() % 1_000_000_000L);
    }

    private static void auditar(com.genlogs.app.model.Auditable entidad, Usuario usuario, String proceso,
            LocalDateTime ahora) {
        entidad.setUserCreate(usuario.getNombreUsuario());
        entidad.setProcessCreate(proceso);
        entidad.setDateCreate(ahora);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String textoOpcional(String valor) {
        return vacio(valor) ? null : valor.trim();
    }
}
