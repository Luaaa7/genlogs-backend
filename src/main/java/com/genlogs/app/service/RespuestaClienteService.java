package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.text.Normalizer;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.RespuestaClienteRequest;
import com.genlogs.app.dto.SeguimientoCotizacionResponse;
import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * El asistente logístico (vendedor) o el jefe registran lo que el cliente
 * respondió fuera del panel. El portal sigue usando el mismo estado final.
 */
@Service
@RequiredArgsConstructor
public class RespuestaClienteService {

    static final String SOLO_VENDEDOR_O_JEFE =
            "Solo el asistente logístico o el jefe administrativo pueden registrar esto";
    /** El test y el flujo anterior citan este texto. */
    static final String SOLO_EL_JEFE = SOLO_VENDEDOR_O_JEFE;
    static final String COMENTARIO_OBLIGATORIO =
            "Indica cómo y cuándo respondió el cliente";
    static final String ADJUNTO_OBLIGATORIO =
            "Adjunta el correo o la orden de compra de la minera";
    static final String DESTINO_NO_ADMITIDO =
            "La respuesta del cliente solo puede ser aprobada o rechazada";
    static final String ACCION_INVALIDA =
            "Indica qué respondió el cliente: aceptó, pidió cambios o rechazó";
    static final String FECHA_OBLIGATORIA = "La fecha de respuesta es obligatoria";
    static final String FECHA_INVALIDA = "La fecha de respuesta no es válida. Usa el formato aaaa-mm-dd";
    static final String FECHA_FUTURA = "La fecha de respuesta no puede ser futura";
    static final String MEDIO_INVALIDO = "Indica el medio: correo, whatsapp, llamada u otro";
    static final String EVIDENCIA_ACEPTACION =
            "Para registrar que el cliente aceptó, escribe una nota o adjunta la evidencia";
    static final String MOTIVO_CAMBIOS = "Indica el motivo del cambio que pidió el cliente";
    static final String MOTIVO_RECHAZO = "Indica el motivo del rechazo";
    static final String SOLO_ENVIADA =
            "Solo se marca como enviada una cotización aprobada por el Jefe";

    private static final String JEFE = "ROLE_" + RolSistema.JEFE_ADMINISTRATIVO.name();
    private static final String VENDEDOR = "ROLE_" + RolSistema.ASISTENTE_LOGISTICO.name();
    private static final String VENDEDOR_LEGACY = "ROLE_VENDEDOR";
    private static final String PROCESO_LEGACY = "RESPUESTA_FUERA_PORTAL";
    private static final String PROCESO_RESPUESTA = "RESPUESTA_CLIENTE";
    private static final String PROCESO_ENVIO = "MARCAR_ENVIADA";
    private static final String CARPETA = "genlogs/cotizaciones/respuestas";
    private static final List<String> COMENTARIOS_TRIGGER = List.of("Cotización registrada", "Cambio de estado");

    private final CotizacionRepository cotizacionRepository;
    private final AdjuntoCotizacionRepository adjuntoCotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PortalCotizacionService portalCotizacionService;
    private final EstadoCotizacionRepository estadoCotizacionRepository;
    private final CloudinaryService cloudinaryService;

    /**
     * Contrato anterior (JSON con codigoEstado). APROBADA se guarda como
     * ACEPTADA_CLIENTE. La orden la sigue creando el controlador.
     */
    @Transactional
    public String registrar(Long idCotizacion, RespuestaClienteRequest request) {
        Usuario usuario = exigirVendedorOJefe();
        String comentario = comentario(request);
        CodigoEstadoCotizacion destino = destino(request);
        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        AdjuntoCotizacion adjunto = exigirAdjuntoNuevo(cotizacion, request == null ? null : request.getIdAdjunto());
        String conArchivo = comentario + " (adjunto: " + adjunto.getNombreArchivo() + ")";
        portalCotizacionService.aplicarDecisionCliente(cotizacion, destino, usuario, conArchivo, PROCESO_LEGACY);
        return destino.name();
    }

    /** Aprobada por el Jefe → Enviada, sin correo. */
    @Transactional
    public void marcarEnviada(Long idCotizacion) {
        Usuario usuario = exigirVendedorOJefe();
        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        String actual = codigoDe(cotizacion);
        if (!CodigoEstadoCotizacion.APROBADA_INTERNA.name().equals(actual)) {
            throw new EstadoNoPermitidoException(SOLO_ENVIADA + ". Ahora está en " + nombreDe(cotizacion) + ".");
        }
        EstadoCotizacion anterior = cotizacion.getEstadoCotizacion();
        EstadoCotizacion enviada = estadoDe(CodigoEstadoCotizacion.ENVIADA);
        aplicar(cotizacion, enviada, usuario, PROCESO_ENVIO);
        if (cotizacion.getFechaEnvio() == null) {
            cotizacion.setFechaEnvio(LocalDateTime.now());
        }
        cotizacionRepository.save(cotizacion);
        guardarSeguimiento(cotizacion, anterior, enviada, usuario, "Marcada como enviada",
                null, null, null, PROCESO_ENVIO);
    }

    /**
     * Enviada o en negociación → aceptada, en negociación o rechazada.
     * @return el seguimiento creado
     */
    @Transactional
    public SeguimientoCotizacionResponse registrarRespuesta(
            Long idCotizacion,
            String accion,
            String fechaRespuesta,
            String medio,
            String nota,
            MultipartFile adjunto) {
        Usuario usuario = exigirVendedorOJefe();
        String accionNormal = normalizarAccion(accion);
        LocalDate fecha = fecha(fechaRespuesta);
        String medioNormal = medio(medio);
        String texto = nota == null ? "" : nota.trim();
        boolean conArchivo = AdjuntoEvidencia.presente(adjunto);
        exigirEvidencia(accionNormal, texto, conArchivo);

        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        String actual = codigoDe(cotizacion);
        if (!CodigoEstadoCotizacion.ENVIADA.name().equals(actual)
                && !CodigoEstadoCotizacion.EN_NEGOCIACION.name().equals(actual)) {
            throw new EstadoNoPermitidoException(
                    "No se puede registrar la respuesta del cliente: la cotización está en "
                            + nombreDe(cotizacion)
                            + ". Solo se responde cuando está enviada o en negociación.");
        }
        CodigoEstadoCotizacion destino = destinoDe(accionNormal);
        EstadoCotizacion anterior = cotizacion.getEstadoCotizacion();
        EstadoCotizacion nuevo = estadoDe(destino);
        String ruta = null;
        if (conArchivo) {
            ruta = guardarAdjunto(cotizacion, usuario, adjunto);
        }
        aplicar(cotizacion, nuevo, usuario, PROCESO_RESPUESTA);
        cotizacionRepository.save(cotizacion);
        SeguimientoCotizacion seguimiento = guardarSeguimiento(
                cotizacion, anterior, nuevo, usuario, texto, fecha, medioNormal, ruta, PROCESO_RESPUESTA);
        return aRespuesta(seguimiento, anterior, nuevo, usuario);
    }

    private static void exigirEvidencia(String accion, String nota, boolean conArchivo) {
        if ("acepto".equals(accion)) {
            if (nota.isEmpty() && !conArchivo) {
                throw new BusinessException(EVIDENCIA_ACEPTACION);
            }
            return;
        }
        if (nota.isEmpty()) {
            throw new BusinessException("pidio_cambios".equals(accion) ? MOTIVO_CAMBIOS : MOTIVO_RECHAZO);
        }
    }

    private static CodigoEstadoCotizacion destinoDe(String accion) {
        return switch (accion) {
            case "acepto" -> CodigoEstadoCotizacion.ACEPTADA_CLIENTE;
            case "pidio_cambios" -> CodigoEstadoCotizacion.EN_NEGOCIACION;
            case "rechazo" -> CodigoEstadoCotizacion.RECHAZADA;
            default -> throw new BusinessException(ACCION_INVALIDA);
        };
    }

    private String guardarAdjunto(Cotizacion cotizacion, Usuario usuario, MultipartFile archivo) {
        AdjuntoEvidencia.Preparado preparado = AdjuntoEvidencia.preparar(archivo);
        String url = preparado.esImagen()
                ? cloudinaryService.subirImagen(archivo, CARPETA)
                : cloudinaryService.subirDocumento(archivo, CARPETA);
        AdjuntoCotizacion adjunto = AdjuntoCotizacion.builder()
                .cotizacion(cotizacion)
                .nombreArchivo(preparado.nombre())
                .urlArchivo(url)
                .build();
        adjunto.setUserCreate(usuario.getNombreUsuario());
        adjunto.setProcessCreate(PROCESO_RESPUESTA);
        adjuntoCotizacionRepository.save(adjunto);
        return url;
    }

    private void aplicar(Cotizacion cotizacion, EstadoCotizacion destino, Usuario usuario, String proceso) {
        cotizacion.setEstadoCotizacion(destino);
        cotizacion.setUserUpdate(usuario.getNombreUsuario());
        cotizacion.setProcessUpdate(proceso);
        cotizacion.setDateUpdate(LocalDateTime.now());
    }

    private SeguimientoCotizacion guardarSeguimiento(
            Cotizacion cotizacion,
            EstadoCotizacion anterior,
            EstadoCotizacion nuevo,
            Usuario usuario,
            String nota,
            LocalDate fechaRespuesta,
            String medio,
            String ruta,
            String proceso) {
        cotizacionRepository.flush();
        SeguimientoCotizacion fila = filaDelTrigger(cotizacion, anterior, nuevo);
        if (fila == null) {
            fila = SeguimientoCotizacion.builder()
                    .cotizacion(cotizacion)
                    .fechaEvento(LocalDateTime.now())
                    .build();
            fila.setUserCreate(usuario.getNombreUsuario());
            fila.setProcessCreate(proceso);
        }
        fila.setEstadoCotizacion(nuevo);
        fila.setEstadoAnterior(anterior);
        fila.setUsuario(usuario);
        fila.setComentario(nota == null || nota.isBlank() ? null : nota);
        fila.setFechaRespuesta(fechaRespuesta);
        fila.setMedio(medio);
        fila.setRutaAdjunto(ruta);
        if (fila.getFechaEvento() == null) {
            fila.setFechaEvento(LocalDateTime.now());
        }
        return seguimientoCotizacionRepository.save(fila);
    }

    /** Solo reutiliza la fila que acaba de insertar el trigger, no un evento viejo. */
    private SeguimientoCotizacion filaDelTrigger(
            Cotizacion cotizacion, EstadoCotizacion anterior, EstadoCotizacion nuevo) {
        if (nuevo.getIdEstadoCotizacion() == null || mismoCodigo(anterior, nuevo)) {
            return null;
        }
        LocalDateTime umbral = LocalDateTime.now().minusSeconds(15);
        return seguimientoCotizacionRepository.findByCotizacion(cotizacion.getIdCotizacion()).stream()
                .filter(s -> s.getEstadoCotizacion() != null
                        && nuevo.getIdEstadoCotizacion().equals(s.getEstadoCotizacion().getIdEstadoCotizacion()))
                .filter(s -> s.getComentario() != null && COMENTARIOS_TRIGGER.contains(s.getComentario()))
                .filter(s -> s.getFechaEvento() != null && !s.getFechaEvento().isBefore(umbral))
                .max(Comparator.comparing(SeguimientoCotizacion::getIdSeguimiento, Comparator.nullsFirst(Long::compareTo)))
                .orElse(null);
    }

    private static boolean mismoCodigo(EstadoCotizacion anterior, EstadoCotizacion nuevo) {
        return anterior != null
                && nuevo != null
                && Objects.equals(anterior.getCodigoEstado(), nuevo.getCodigoEstado());
    }

    private EstadoCotizacion estadoDe(CodigoEstadoCotizacion codigo) {
        return estadoCotizacionRepository.findByCodigoEstado(codigo.name())
                .orElseThrow(() -> new BusinessException("No existe el estado " + codigo.name() + " configurado"));
    }

    private static SeguimientoCotizacionResponse aRespuesta(
            SeguimientoCotizacion s, EstadoCotizacion anterior, EstadoCotizacion nuevo, Usuario usuario) {
        String codigoNuevo = nuevo == null ? null : nuevo.getCodigoEstado();
        String codigoAnterior = anterior == null ? null : anterior.getCodigoEstado();
        String nombre = usuario == null ? null : usuario.getNombreUsuario();
        return SeguimientoCotizacionResponse.builder()
                .idSeguimiento(s.getIdSeguimiento())
                .id(s.getIdSeguimiento())
                .idEstadoCotizacion(nuevo == null ? null : nuevo.getIdEstadoCotizacion())
                .estado(nuevo == null ? null : nuevo.getNombreEstado())
                .codigoEstado(codigoNuevo)
                .estadoCodigo(codigoNuevo)
                .estadoNuevo(codigoNuevo)
                .estadoAnterior(codigoAnterior)
                .usuario(nombre)
                .usuarioNombre(nombre)
                .fechaEvento(s.getFechaEvento() == null
                        ? null
                        : s.getFechaEvento().atZone(ZoneId.systemDefault()).toInstant())
                .fecha(s.getFechaEvento() == null
                        ? null
                        : s.getFechaEvento().atZone(ZoneId.systemDefault()).toInstant())
                .comentario(s.getComentario())
                .observaciones(s.getComentario())
                .fechaRespuesta(s.getFechaRespuesta())
                .medio(s.getMedio())
                .rutaAdjunto(s.getRutaAdjunto())
                .build();
    }

    private Usuario exigirVendedorOJefe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean permitido = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> JEFE.equals(a.getAuthority())
                        || VENDEDOR.equals(a.getAuthority())
                        || VENDEDOR_LEGACY.equals(a.getAuthority()));
        if (!permitido || auth.getName() == null || auth.getName().isBlank()) {
            throw new AccessDeniedException(SOLO_VENDEDOR_O_JEFE);
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new AccessDeniedException(SOLO_VENDEDOR_O_JEFE));
    }

    private static String comentario(RespuestaClienteRequest request) {
        String texto = request == null || request.getComentario() == null ? "" : request.getComentario().trim();
        if (texto.isEmpty()) {
            throw new BusinessException(COMENTARIO_OBLIGATORIO);
        }
        return texto;
    }

    private static CodigoEstadoCotizacion destino(RespuestaClienteRequest request) {
        String codigo = request == null || request.getCodigoEstado() == null
                ? ""
                : request.getCodigoEstado().trim();
        if (CodigoEstadoCotizacion.esAceptacionCliente(codigo)
                && !CodigoEstadoCotizacion.APROBADA_INTERNA.name().equalsIgnoreCase(codigo)) {
            return CodigoEstadoCotizacion.ACEPTADA_CLIENTE;
        }
        if (CodigoEstadoCotizacion.RECHAZADA.name().equalsIgnoreCase(codigo)) {
            return CodigoEstadoCotizacion.RECHAZADA;
        }
        throw new BusinessException(DESTINO_NO_ADMITIDO);
    }

    static String normalizarAccion(String accion) {
        String texto = accion == null ? "" : sinAcentos(accion).trim().toLowerCase(Locale.ROOT);
        texto = texto.replace('-', '_').replace(' ', '_');
        texto = texto.replaceAll("_+", "_");
        if ("acepto".equals(texto) || "pidio_cambios".equals(texto) || "rechazo".equals(texto)) {
            return texto;
        }
        throw new BusinessException(ACCION_INVALIDA);
    }

    static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new BusinessException(FECHA_OBLIGATORIA);
        }
        String recorte = texto.trim();
        if (recorte.length() >= 10) {
            recorte = recorte.substring(0, 10);
        }
        LocalDate dia;
        try {
            dia = LocalDate.parse(recorte);
        } catch (DateTimeParseException e) {
            throw new BusinessException(FECHA_INVALIDA);
        }
        if (dia.isAfter(LocalDate.now(PlazoDespacho.LIMA))) {
            throw new BusinessException(FECHA_FUTURA);
        }
        return dia;
    }

    static String medio(String texto) {
        String limpio = texto == null ? "" : sinAcentos(texto).trim().toLowerCase(Locale.ROOT);
        if ("correo".equals(limpio) || "whatsapp".equals(limpio) || "llamada".equals(limpio) || "otro".equals(limpio)) {
            return limpio;
        }
        throw new BusinessException(MEDIO_INVALIDO);
    }

    private static String sinAcentos(String texto) {
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normal.replaceAll("\\p{M}", "");
    }

    private static String codigoDe(Cotizacion cotizacion) {
        return cotizacion.getEstadoCotizacion() == null
                ? ""
                : cotizacion.getEstadoCotizacion().getCodigoEstado();
    }

    private static String nombreDe(Cotizacion cotizacion) {
        if (cotizacion.getEstadoCotizacion() == null) {
            return "sin estado";
        }
        String nombre = cotizacion.getEstadoCotizacion().getNombreEstado();
        return nombre == null || nombre.isBlank()
                ? cotizacion.getEstadoCotizacion().getCodigoEstado()
                : nombre;
    }

    /**
     * Vale el adjunto activo de esta cotización subido después del primer paso a ENVIADA.
     * Un archivo del borrador, o uno anterior o igual a ese envío, no sirve.
     */
    private AdjuntoCotizacion exigirAdjuntoNuevo(Cotizacion cotizacion, Long idAdjunto) {
        if (idAdjunto == null) {
            throw new BusinessException(ADJUNTO_OBLIGATORIO);
        }
        AdjuntoCotizacion adjunto = adjuntoCotizacionRepository.findById(idAdjunto)
                .orElseThrow(() -> new BusinessException(ADJUNTO_OBLIGATORIO));
        Long dueno = adjunto.getCotizacion() == null ? null : adjunto.getCotizacion().getIdCotizacion();
        LocalDateTime envio = fechaDelPrimerEnvio(cotizacion.getIdCotizacion());
        if (!cotizacion.getIdCotizacion().equals(dueno)
                || !"A".equals(adjunto.getStatus())
                || envio == null
                || adjunto.getDateCreate() == null
                || !adjunto.getDateCreate().isAfter(envio)) {
            throw new BusinessException(ADJUNTO_OBLIGATORIO);
        }
        return adjunto;
    }

    /** El seguimiento ENVIADA más antiguo. Un reenvío posterior no mueve esta fecha. */
    private LocalDateTime fechaDelPrimerEnvio(Long idCotizacion) {
        return seguimientoCotizacionRepository.findByCotizacion(idCotizacion).stream()
                .filter(s -> s.getEstadoCotizacion() != null
                        && CodigoEstadoCotizacion.ENVIADA.name().equals(s.getEstadoCotizacion().getCodigoEstado()))
                .map(SeguimientoCotizacion::getFechaEvento)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
