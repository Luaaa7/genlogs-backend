package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.gestion.EventoSolicitudResponse;
import com.genlogs.app.dto.gestion.SolicitudInternaResponse;
import com.genlogs.app.enums.EstadoSolicitud;
import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.enums.TipoPlazo;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.SolicitudEvento;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.SolicitudEventoRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * BE-B · Gestión interna de la solicitud (decisión 9 de la v3.1):
 * - iniciar: PENDIENTE → EN_REVISION y se asigna a quien la toma (si nadie la tenía).
 * - pausar: EN_REVISION → EN_ESPERA_TERCERO con motivo y fecha estimada; el reloj se detiene.
 * - reanudar: vuelve a EN_REVISION y el plazo se corre lo que estuvo pausado (en tiempo hábil).
 * - reclasificar: cambia el tipo de plazo y recalcula desde la recepción. La primera vez la hace
 *   el asistente; la segunda y siguientes, solo el Jefe Administrativo.
 * - reasignar: solo el Jefe Administrativo, a otro usuario interno activo.
 * Cada cambio queda en la bitácora. Sobre COTIZADA, RECHAZADA o CANCELADA no se hace nada (409).
 */
@Service
public class GestionSolicitudService {

    private static final Set<String> FINALES = Set.of(
            EstadoSolicitud.COTIZADA.name(), EstadoSolicitud.RECHAZADA.name(), EstadoSolicitud.CANCELADA.name());
    private static final String JEFE = "ROLE_JEFE_ADMINISTRATIVO";

    private final SolicitudCotizacionRepository solicitudRepository;
    private final SolicitudEventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlazoSolicitud plazo;
    private final CalendarioHabil calendario;
    private final BitacoraSolicitud bitacora;

    public GestionSolicitudService(SolicitudCotizacionRepository solicitudRepository,
            SolicitudEventoRepository eventoRepository, UsuarioRepository usuarioRepository, PlazoSolicitud plazo,
            CalendarioHabil calendario, BitacoraSolicitud bitacora) {
        this.solicitudRepository = solicitudRepository;
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
        this.plazo = plazo;
        this.calendario = calendario;
        this.bitacora = bitacora;
    }

    @Transactional
    public SolicitudInternaResponse iniciar(Long id) {
        Usuario yo = usuarioActual();
        SolicitudCotizacion s = abierta(id);
        exigirEstado(s, EstadoSolicitud.PENDIENTE, "Solo se puede iniciar una solicitud pendiente");
        s.setEstadoSolicitud(EstadoSolicitud.EN_REVISION.name());
        if (s.getUsuarioAsignado() == null) {
            s.setUsuarioAsignado(yo);
        }
        guardar(s, yo, "SOLICITUD_INICIAR");
        bitacora.registrar(id, yo.getIdUsuario(), TipoEventoSolicitud.ESTADO, "PENDIENTE → EN_REVISION");
        return aRespuesta(s);
    }

    @Transactional
    public SolicitudInternaResponse pausar(Long id, String motivo, LocalDate fechaEstimada) {
        Usuario yo = usuarioActual();
        SolicitudCotizacion s = abierta(id);
        exigirEstado(s, EstadoSolicitud.EN_REVISION, "Solo se puede pausar una solicitud en revisión");
        s.setEstadoSolicitud(EstadoSolicitud.EN_ESPERA_TERCERO.name());
        s.setPausadaDesde(LocalDateTime.now());
        s.setMotivoPausa(motivo.trim());
        s.setFechaEstimadaTercero(fechaEstimada);
        guardar(s, yo, "SOLICITUD_PAUSAR");
        bitacora.registrar(id, yo.getIdUsuario(), TipoEventoSolicitud.PAUSA,
                "Motivo: " + motivo.trim() + (fechaEstimada == null ? "" : "; fecha estimada " + fechaEstimada));
        return aRespuesta(s);
    }

    @Transactional
    public SolicitudInternaResponse reanudar(Long id) {
        Usuario yo = usuarioActual();
        SolicitudCotizacion s = abierta(id);
        exigirEstado(s, EstadoSolicitud.EN_ESPERA_TERCERO, "La solicitud no está pausada");
        LocalDateTime ahora = LocalDateTime.now();
        long restante = calendario.minutosHabiles(s.getPausadaDesde(), s.getFechaLimiteInterna());
        LocalDateTime anterior = s.getFechaLimiteInterna();
        if (restante > 0) {
            // Si ya estaba vencida al pausar, no se le regala plazo.
            s.setFechaLimiteInterna(calendario.sumarMinutosHabiles(ahora, restante));
        }
        s.setEstadoSolicitud(EstadoSolicitud.EN_REVISION.name());
        s.setPausadaDesde(null);
        s.setMotivoPausa(null);
        s.setFechaEstimadaTercero(null);
        guardar(s, yo, "SOLICITUD_REANUDAR");
        bitacora.registrar(id, yo.getIdUsuario(), TipoEventoSolicitud.REANUDA,
                "Plazo interno: " + anterior + " → " + s.getFechaLimiteInterna());
        return aRespuesta(s);
    }

    @Transactional
    public SolicitudInternaResponse reclasificar(Long id, TipoPlazo nuevo) {
        Usuario yo = usuarioActual();
        SolicitudCotizacion s = abierta(id);
        String anterior = s.getTipoPlazo();
        if (nuevo.name().equals(anterior)) {
            throw new BusinessException("La solicitud ya tiene el plazo " + nuevo.name());
        }
        int veces = s.getReclasificaciones() == null ? 0 : s.getReclasificaciones();
        if (veces >= 1 && !esJefe()) {
            throw new AccessDeniedException("La segunda reclasificación la hace el Jefe Administrativo");
        }
        s.setTipoPlazo(nuevo.name());
        s.setReclasificaciones((short) (veces + 1));
        s.setFechaLimiteInterna(plazo.fechaLimite(s.getDateCreate(), nuevo));
        guardar(s, yo, "SOLICITUD_RECLASIFICAR");
        bitacora.registrar(id, yo.getIdUsuario(), TipoEventoSolicitud.RECLASIFICA, anterior + " → " + nuevo.name());
        return aRespuesta(s);
    }

    @Transactional
    public SolicitudInternaResponse reasignar(Long id, Long idUsuario) {
        if (!esJefe()) {
            throw new AccessDeniedException("Solo el Jefe Administrativo reasigna solicitudes");
        }
        Usuario yo = usuarioActual();
        SolicitudCotizacion s = abierta(id);
        Usuario nuevo = usuarioRepository.findById(idUsuario)
                .filter(u -> u.getCliente() == null && u.getStatus() != null && u.getStatus().trim().equals("A")
                        && !Boolean.TRUE.equals(u.getBloqueado()))
                .orElseThrow(() -> new BusinessException("Elige un usuario interno activo"));
        String anterior = s.getUsuarioAsignado() == null ? "sin asignar" : s.getUsuarioAsignado().getNombreUsuario();
        s.setUsuarioAsignado(nuevo);
        guardar(s, yo, "SOLICITUD_REASIGNAR");
        bitacora.registrar(id, yo.getIdUsuario(), TipoEventoSolicitud.REASIGNA, anterior + " → " + nuevo.getNombreUsuario());
        return aRespuesta(s);
    }

    @Transactional(readOnly = true)
    public List<EventoSolicitudResponse> historial(Long id) {
        if (!solicitudRepository.existsById(id)) {
            throw new ResourceNotFoundException("Solicitud no encontrada");
        }
        List<SolicitudEvento> eventos = eventoRepository.findByIdSolicitudOrderByIdEventoAsc(id);
        Set<Long> ids = eventos.stream().map(SolicitudEvento::getIdUsuario)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nombres = usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario, Usuario::getNombreUsuario));
        return eventos.stream()
                .map(e -> new EventoSolicitudResponse(e.getTipoEvento(), e.getDetalle(),
                        e.getIdUsuario() == null ? null : nombres.get(e.getIdUsuario()), e.getFechaEvento()))
                .toList();
    }

    /** Vista interna de una solicitud (la usan también la lista por vencimiento y el detalle). */
    public static SolicitudInternaResponse aRespuesta(SolicitudCotizacion s) {
        Usuario a = s.getUsuarioAsignado();
        String cliente = s.getCliente() == null || s.getCliente().getTercero() == null
                ? null : s.getCliente().getTercero().getRazonSocial();
        return new SolicitudInternaResponse(s.getIdSolicitud(), s.getCodigoSolicitud(), s.getAsunto(), cliente,
                s.getEstadoSolicitud(), s.getTipoPlazo(), s.getDateCreate(), s.getFechaLimiteInterna(),
                s.getPausadaDesde(), s.getMotivoPausa(), s.getFechaEstimadaTercero(), s.getReclasificaciones(),
                a == null ? null : new SolicitudInternaResponse.UsuarioResumen(a.getIdUsuario(), a.getNombreUsuario(),
                        a.getNombres()));
    }

    // ------------------------------------------------------------------ apoyo

    private SolicitudCotizacion abierta(Long id) {
        SolicitudCotizacion s = solicitudRepository.findById(id)
                .filter(x -> x.getStatus() != null && x.getStatus().trim().equals("A"))
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));
        if (FINALES.contains(s.getEstadoSolicitud())) {
            throw new EstadoNoPermitidoException("La solicitud está " + s.getEstadoSolicitud().toLowerCase()
                    + " y ya no se puede cambiar");
        }
        return s;
    }

    private static void exigirEstado(SolicitudCotizacion s, EstadoSolicitud esperado, String mensaje) {
        if (!esperado.name().equals(s.getEstadoSolicitud())) {
            throw new EstadoNoPermitidoException(mensaje + " (está " + s.getEstadoSolicitud() + ")");
        }
    }

    private void guardar(SolicitudCotizacion s, Usuario yo, String proceso) {
        s.setUserUpdate(yo.getNombreUsuario());
        s.setProcessUpdate(proceso);
        s.setDateUpdate(LocalDateTime.now());
        solicitudRepository.save(s);
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("Sin sesión");
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .filter(u -> u.getCliente() == null)
                .orElseThrow(() -> new AccessDeniedException("Solo el personal interno gestiona solicitudes"));
    }

    private static boolean esJefe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> JEFE.equals(a.getAuthority()));
    }
}
