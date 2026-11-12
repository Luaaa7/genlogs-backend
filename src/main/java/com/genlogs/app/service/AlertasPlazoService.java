package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.EstadoSolicitud;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.SolicitudEvento;
import com.genlogs.app.repository.AvisoPlazoRepository;
import com.genlogs.app.repository.SolicitudEventoRepository;
import com.genlogs.app.service.VencimientoSolicitudService.Semaforo;

/**
 * BE-B · Tarea programada de alertas de plazo (decisión 9, solo interno):
 * - al llegar al % de alerta (75 por defecto): aviso al asistente asignado, o a todos los
 *   asistentes logísticos si nadie la tomó;
 * - al vencer: aviso al Jefe Administrativo, también por correo (D-21).
 * Cada aviso sale una vez por plazo: si después se reclasifica o se reanuda, vuelve a contar.
 * Corre cada 15 minutos en horario de atención (lun–sáb, hora de Lima).
 */
@Service
public class AlertasPlazoService {

    private static final Logger log = LoggerFactory.getLogger(AlertasPlazoService.class);
    private static final List<String> CAMBIOS_DE_PLAZO = List.of(
            TipoEventoSolicitud.RECLASIFICA.name(), TipoEventoSolicitud.REANUDA.name());

    private final VencimientoSolicitudService vencimiento;
    private final NotificacionService notificaciones;
    private final AvisoPlazoRepository avisos;
    private final SolicitudEventoRepository eventos;

    public AlertasPlazoService(VencimientoSolicitudService vencimiento, NotificacionService notificaciones,
            AvisoPlazoRepository avisos, SolicitudEventoRepository eventos) {
        this.vencimiento = vencimiento;
        this.notificaciones = notificaciones;
        this.avisos = avisos;
        this.eventos = eventos;
    }

    @Scheduled(cron = "${genlogs.alertas-plazo.cron:0 */15 8-18 * * MON-SAT}", zone = "America/Lima")
    public void tarea() {
        try {
            int enviados = revisarPlazos(LocalDateTime.now(ZoneId.of("America/Lima")));
            if (enviados > 0) {
                log.info("Alertas de plazo enviadas: {}", enviados);
            }
        } catch (RuntimeException e) {
            log.error("Falló la revisión de plazos", e);
        }
    }

    /** Revisa las solicitudes abiertas y avisa lo que corresponda. Devuelve cuántas alertas salieron. */
    @Transactional
    public int revisarPlazos(LocalDateTime ahora) {
        int enviados = 0;
        for (SolicitudCotizacion s : vencimiento.abiertas()) {
            if (EstadoSolicitud.EN_ESPERA_TERCERO.name().equals(s.getEstadoSolicitud())) {
                continue;
            }
            Semaforo color = vencimiento.semaforo(s.getEstadoSolicitud(), s.getDateCreate(),
                    s.getFechaLimiteInterna(), ahora);
            String enlace = "/solicitudes/" + s.getIdSolicitud();
            if (color == Semaforo.ROJO && !yaAvisado(s, TipoAviso.PLAZO_VENCIDO, enlace)) {
                notificaciones.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.PLAZO_VENCIDO,
                        "Plazo vencido: " + s.getCodigoSolicitud(),
                        "La solicitud " + s.getCodigoSolicitud() + " (" + s.getAsunto() + ") venció el "
                                + s.getFechaLimiteInterna() + responsable(s) + ".",
                        enlace, true);
                enviados++;
            } else if (color == Semaforo.AMARILLO && !yaAvisado(s, TipoAviso.PLAZO_POR_VENCER, enlace)) {
                String titulo = "Por vencer: " + s.getCodigoSolicitud();
                String mensaje = "A la solicitud " + s.getCodigoSolicitud() + " (" + s.getAsunto()
                        + ") le queda poco plazo: vence el " + s.getFechaLimiteInterna() + ".";
                if (s.getUsuarioAsignado() != null) {
                    notificaciones.avisarUsuario(s.getUsuarioAsignado().getIdUsuario(), TipoAviso.PLAZO_POR_VENCER,
                            titulo, mensaje, enlace);
                } else {
                    notificaciones.avisarRol(RolSistema.ASISTENTE_LOGISTICO, TipoAviso.PLAZO_POR_VENCER,
                            titulo, mensaje + " Nadie la ha tomado todavía.", enlace, false);
                }
                enviados++;
            }
        }
        return enviados;
    }

    /** Ya se avisó si hay un aviso de ese tipo posterior al último cambio de plazo. */
    private boolean yaAvisado(SolicitudCotizacion s, TipoAviso tipo, String enlace) {
        LocalDateTime desde = eventos
                .findFirstByIdSolicitudAndTipoEventoInOrderByFechaEventoDesc(s.getIdSolicitud(), CAMBIOS_DE_PLAZO)
                .map(SolicitudEvento::getFechaEvento)
                .orElse(s.getDateCreate());
        return avisos.existsByTipoAndEnlaceAndFechaCreacionAfter(tipo.name(), enlace, desde);
    }

    private static String responsable(SolicitudCotizacion s) {
        return s.getUsuarioAsignado() == null ? " y nadie la tiene asignada"
                : "; la tiene " + s.getUsuarioAsignado().getNombres();
    }
}
