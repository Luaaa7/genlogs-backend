package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.gestion.SolicitudVencimientoResponse;
import com.genlogs.app.enums.EstadoSolicitud;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.repository.SolicitudCotizacionRepository;

/**
 * BE-B · Lista interna ordenada por vencimiento con semáforo (decisión 9, solo interno):
 * VERDE antes del % de alerta (ALERTA_PLAZO_PCT, 75 por defecto), AMARILLO desde ahí,
 * ROJO si ya venció y PAUSADA si está en espera de tercero (el reloj no corre).
 */
@Service
public class VencimientoSolicitudService {

    public enum Semaforo { VERDE, AMARILLO, ROJO, PAUSADA }

    static final Set<String> ABIERTAS = Set.of(EstadoSolicitud.PENDIENTE.name(),
            EstadoSolicitud.EN_REVISION.name(), EstadoSolicitud.EN_ESPERA_TERCERO.name());
    private static final int ALERTA_POR_DEFECTO = 75;

    private final SolicitudCotizacionRepository solicitudRepository;
    private final CalendarioHabil calendario;
    private final ParametrosService parametros;

    public VencimientoSolicitudService(SolicitudCotizacionRepository solicitudRepository, CalendarioHabil calendario,
            ParametrosService parametros) {
        this.solicitudRepository = solicitudRepository;
        this.calendario = calendario;
        this.parametros = parametros;
    }

    @Transactional(readOnly = true)
    public List<SolicitudVencimientoResponse> lista(LocalDateTime ahora, String soloDeUsuario) {
        return abiertas().stream()
                .filter(s -> soloDeUsuario == null || (s.getUsuarioAsignado() != null
                        && soloDeUsuario.equals(s.getUsuarioAsignado().getNombreUsuario())))
                .map(s -> new SolicitudVencimientoResponse(GestionSolicitudService.aRespuesta(s),
                        semaforo(s.getEstadoSolicitud(), s.getDateCreate(), s.getFechaLimiteInterna(), ahora),
                        porcentaje(s.getEstadoSolicitud(), s.getDateCreate(), s.getFechaLimiteInterna(), ahora)))
                .toList();
    }

    /** Abiertas, de la que vence antes a la que vence después (las sin plazo al final). */
    List<SolicitudCotizacion> abiertas() {
        List<SolicitudCotizacion> todas = solicitudRepository
                .findByEstadoSolicitudInAndStatusOrderByFechaLimiteInternaAsc(ABIERTAS, "A");
        return java.util.stream.Stream.concat(
                todas.stream().filter(s -> s.getFechaLimiteInterna() != null),
                todas.stream().filter(s -> s.getFechaLimiteInterna() == null)).toList();
    }

    public Semaforo semaforo(String estado, LocalDateTime recibida, LocalDateTime limite, LocalDateTime ahora) {
        if (EstadoSolicitud.EN_ESPERA_TERCERO.name().equals(estado)) {
            return Semaforo.PAUSADA;
        }
        if (limite == null) {
            return Semaforo.VERDE;
        }
        if (!ahora.isBefore(limite)) {
            return Semaforo.ROJO;
        }
        Integer pct = porcentaje(estado, recibida, limite, ahora);
        return pct != null && pct >= alertaPct() ? Semaforo.AMARILLO : Semaforo.VERDE;
    }

    /** % del plazo hábil ya usado; null si está pausada o no tiene plazo. */
    public Integer porcentaje(String estado, LocalDateTime recibida, LocalDateTime limite, LocalDateTime ahora) {
        if (limite == null || recibida == null || EstadoSolicitud.EN_ESPERA_TERCERO.name().equals(estado)) {
            return null;
        }
        long total = calendario.minutosHabiles(recibida, limite);
        if (total <= 0) {
            return 100;
        }
        long usado = calendario.minutosHabiles(recibida, ahora);
        return (int) (usado * 100 / total);
    }

    int alertaPct() {
        return parametros.buscar(ClavesParametro.ALERTA_PLAZO_PCT).map(v -> {
            try {
                int pct = Integer.parseInt(v);
                return pct >= 1 && pct <= 100 ? pct : ALERTA_POR_DEFECTO;
            } catch (NumberFormatException e) {
                return ALERTA_POR_DEFECTO;
            }
        }).orElse(ALERTA_POR_DEFECTO);
    }
}
