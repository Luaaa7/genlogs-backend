package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.text.Normalizer;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.EnviarCotizacionRequest;
import com.genlogs.app.dto.EnviarCotizacionResponse;
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
    static final String DESTINO_NO_ADMITIDO =
            "La respuesta del cliente solo puede ser aceptada, en negociación o rechazada";
    static final String ACCION_INVALIDA =
            "Indica qué respondió el cliente: aceptó, pidió cambios o rechazó";
    static final String FECHA_OBLIGATORIA = "La fecha de respuesta es obligatoria";
    static final String FECHA_INVALIDA = "La fecha de respuesta no es válida. Usa el formato aaaa-mm-dd";
    static final String FECHA_FUTURA = "La fecha de respuesta no puede ser futura";
    static final String MEDIO_INVALIDO = "Indica el medio: correo, whatsapp, llamada u otro";
    static final int MAX_DETALLE_MEDIO = 60;
    static final String DETALLE_MEDIO_LARGO = "El detalle del medio no puede superar 60 caracteres";
    static final String EVIDENCIA_ACEPTACION =
            "Para registrar que el cliente aceptó, escribe una nota o adjunta la evidencia";
    static final String MOTIVO_CAMBIOS = "Indica el motivo del cambio que pidió el cliente";
    static final String MOTIVO_RECHAZO = "Indica el motivo del rechazo";
    static final String SOLO_ENVIADA =
            "Solo se marca como enviada una cotización aprobada por el Jefe";
    static final String SIN_CORREO =
            "El cliente no tiene un correo para enviar la cotización.";

    private static final String JEFE = "ROLE_" + RolSistema.JEFE_ADMINISTRATIVO.name();
    private static final String VENDEDOR = "ROLE_" + RolSistema.ASISTENTE_LOGISTICO.name();
    private static final String PROCESO_RESPUESTA = "RESPUESTA_CLIENTE";
    private static final String CARPETA = "genlogs/cotizaciones/respuestas";
    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final List<String> COMENTARIOS_TRIGGER = List.of("Cotización registrada", "Cambio de estado");

    private final CotizacionRepository cotizacionRepository;
    private final AdjuntoCotizacionRepository adjuntoCotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstadoCotizacionRepository estadoCotizacionRepository;
    private final CloudinaryService cloudinaryService;
    private final CotizacionService cotizacionService;

    /**
     * Lo que manda la pantalla: codigoEstado, comentario, idAdjunto, fechaRespuesta y medio.
     * Guarda ACEPTADA_CLIENTE, EN_NEGOCIACION o RECHAZADA. No llama a
     * {@link CotizacionService#registrarOrdenSiAprobada}: el controlador lo hace
     * después de que este método retorna, para que un fallo de la orden no deshaga la aceptación.
     */
    @Transactional
    public String registrar(Long idCotizacion, RespuestaClienteRequest request) {
        Usuario usuario = exigirVendedorOJefe();
        CodigoEstadoCotizacion destino = destino(request);
        LocalDate dia = fecha(request == null ? null : request.getFechaRespuesta());
        String medioNormal = medio(request == null ? null : request.getMedio());
        String detalleMedio = detalleMedio(medioNormal, request == null ? null : request.getMedioDetalle());
        String texto = request == null || request.getComentario() == null ? "" : request.getComentario().trim();
        Long idAdjunto = request == null ? null : request.getIdAdjunto();
        exigirTexto(destino, texto, idAdjunto);

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
        AdjuntoCotizacion adjunto = idAdjunto == null ? null : adjuntoDeLaCotizacion(cotizacion, idAdjunto);
        EstadoCotizacion anterior = cotizacion.getEstadoCotizacion();
        EstadoCotizacion nuevo = estadoDe(destino);
        aplicar(cotizacion, nuevo, usuario, PROCESO_RESPUESTA);
        cotizacionRepository.save(cotizacion);
        guardarSeguimiento(cotizacion, anterior, nuevo, usuario, texto, dia, medioNormal, detalleMedio,
                adjunto == null ? null : adjunto.getUrlArchivo(), adjunto, PROCESO_RESPUESTA);
        return destino.name();
    }

    /**
     * Aprobada por el Jefe → correo de la cotización y, si sale, ENVIADA.
     * Reutiliza {@link CotizacionService#enviar}.
     */
    public void marcarEnviada(Long idCotizacion) {
        exigirVendedorOJefe();
        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        String actual = codigoDe(cotizacion);
        if (!CodigoEstadoCotizacion.APROBADA_INTERNA.name().equals(actual)) {
            throw new EstadoNoPermitidoException(SOLO_ENVIADA + ". Ahora está en " + nombreDe(cotizacion) + ".");
        }
        String correo = correoDelCliente(cotizacion);
        if (!correoValido(correo)) {
            throw new BusinessException(SIN_CORREO);
        }
        String codigo = cotizacion.getCodigoCotizacion() == null ? "" : cotizacion.getCodigoCotizacion().trim();
        EnviarCotizacionRequest envio = new EnviarCotizacionRequest();
        envio.setCorreoDestinatario(correo);
        envio.setAsunto(codigo.isEmpty() ? "Cotización" : "Cotización " + codigo);
        envio.setMensaje("");
        envio.setIncluirDetalles(true);
        EnviarCotizacionResponse respuesta = cotizacionService.enviar(idCotizacion, envio);
        if (respuesta == null || !respuesta.isSuccess()) {
            String mensaje = respuesta == null || respuesta.getMensaje() == null || respuesta.getMensaje().isBlank()
                    ? "No se pudo enviar el correo. Intenta de nuevo o revisa la configuración de correo."
                    : respuesta.getMensaje();
            throw new BusinessException(mensaje);
        }
    }

    /**
     * Enviada o en negociación → aceptada, en negociación o rechazada.
     * No crea la orden: el controlador llama a
     * {@link CotizacionService#registrarOrdenSiAprobada} con el código guardado
     * después de que este método retorna.
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
                cotizacion, anterior, nuevo, usuario, texto, fecha, medioNormal, null, ruta, null, PROCESO_RESPUESTA);
        return aRespuesta(seguimiento, anterior, nuevo, usuario);
    }

    private static void exigirTexto(CodigoEstadoCotizacion destino, String nota, Long idAdjunto) {
        if (destino == CodigoEstadoCotizacion.ACEPTADA_CLIENTE) {
            if (nota.isEmpty() && idAdjunto == null) {
                throw new BusinessException(EVIDENCIA_ACEPTACION);
            }
            return;
        }
        if (nota.isEmpty()) {
            throw new BusinessException(destino == CodigoEstadoCotizacion.EN_NEGOCIACION
                    ? MOTIVO_CAMBIOS
                    : MOTIVO_RECHAZO);
        }
    }

    private AdjuntoCotizacion adjuntoDeLaCotizacion(Cotizacion cotizacion, Long idAdjunto) {
        AdjuntoCotizacion adjunto = adjuntoCotizacionRepository.findById(idAdjunto)
                .orElseThrow(() -> new BusinessException("No se encontró el adjunto"));
        Long dueno = adjunto.getCotizacion() == null ? null : adjunto.getCotizacion().getIdCotizacion();
        if (!cotizacion.getIdCotizacion().equals(dueno) || !"A".equals(adjunto.getStatus())) {
            throw new BusinessException("El adjunto no pertenece a la cotización");
        }
        return adjunto;
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
            String medioDetalle,
            String ruta,
            AdjuntoCotizacion adjunto,
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
        fila.setMedioDetalle(medioDetalle);
        fila.setRutaAdjunto(ruta);
        fila.setAdjunto(adjunto);
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
        String codigoNuevo = nuevo == null ? null : CodigoEstadoCotizacion.visible(nuevo.getCodigoEstado());
        String codigoAnterior = anterior == null
                ? null
                : CodigoEstadoCotizacion.visible(anterior.getCodigoEstado());
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
                .medioDetalle(s.getMedioDetalle())
                .rutaAdjunto(s.getRutaAdjunto())
                .rutaVersionPdf(s.getRutaVersionPdf())
                .idAdjunto(s.getAdjunto() == null ? null : s.getAdjunto().getIdAdjunto())
                .build();
    }

    private Usuario exigirVendedorOJefe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean permitido = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> JEFE.equals(a.getAuthority()) || VENDEDOR.equals(a.getAuthority()));
        if (!permitido || auth.getName() == null || auth.getName().isBlank()) {
            throw new AccessDeniedException(SOLO_VENDEDOR_O_JEFE);
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new AccessDeniedException(SOLO_VENDEDOR_O_JEFE));
    }

    private static CodigoEstadoCotizacion destino(RespuestaClienteRequest request) {
        String codigo = request == null || request.getCodigoEstado() == null
                ? ""
                : request.getCodigoEstado().trim();
        if (CodigoEstadoCotizacion.ACEPTADA_CLIENTE.name().equalsIgnoreCase(codigo)) {
            return CodigoEstadoCotizacion.ACEPTADA_CLIENTE;
        }
        if (CodigoEstadoCotizacion.EN_NEGOCIACION.name().equalsIgnoreCase(codigo)) {
            return CodigoEstadoCotizacion.EN_NEGOCIACION;
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

    /** El detalle solo vale cuando el medio es "otro"; vacío se guarda como nulo. */
    static String detalleMedio(String medio, String texto) {
        if (!"otro".equals(medio) || texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > MAX_DETALLE_MEDIO) {
            throw new BusinessException(DETALLE_MEDIO_LARGO);
        }
        return limpio;
    }

    private static String sinAcentos(String texto) {
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normal.replaceAll("\\p{M}", "");
    }

    /** El mismo correo que muestra el detalle: el del contacto y, si no es válido, el del cliente. */
    private static String correoDelCliente(Cotizacion cotizacion) {
        String contacto = cotizacion.getContacto() == null ? "" : texto(cotizacion.getContacto().getCorreo());
        String delCliente = cotizacion.getCliente() == null || cotizacion.getCliente().getTercero() == null
                ? ""
                : texto(cotizacion.getCliente().getTercero().getCorreo());
        if (correoValido(contacto)) {
            return contacto;
        }
        if (correoValido(delCliente)) {
            return delCliente;
        }
        return contacto.isEmpty() ? delCliente : contacto;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static boolean correoValido(String correo) {
        return correo != null && CORREO.matcher(correo).matches();
    }

    private static String codigoDe(Cotizacion cotizacion) {
        if (cotizacion.getEstadoCotizacion() == null) {
            return "";
        }
        return CodigoEstadoCotizacion.visible(cotizacion.getEstadoCotizacion().getCodigoEstado());
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

}