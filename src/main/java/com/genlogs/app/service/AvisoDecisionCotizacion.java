package com.genlogs.app.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Aviso en la campanita a quien preparó la cotización cuando el Jefe la aprueba o la devuelve,
 * con el enlace directo. No avisa a quien toma la decisión.
 */
@Component
public class AvisoDecisionCotizacion {

    private final CotizacionRepository cotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificaciones;

    public AvisoDecisionCotizacion(CotizacionRepository cotizacionRepository, UsuarioRepository usuarioRepository,
            NotificacionService notificaciones) {
        this.cotizacionRepository = cotizacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificaciones = notificaciones;
    }

    public void aprobada(Long idCotizacion) {
        avisar(idCotizacion, TipoAviso.COTIZACION_APROBADA, "Cotización aprobada",
                "El Jefe aprobó la cotización. Ya puedes enviarla al cliente.");
    }

    public void devuelta(Long idCotizacion, String motivo) {
        avisar(idCotizacion, TipoAviso.COTIZACION_DEVUELTA, "Cotización devuelta",
                "El Jefe la devolvió para corregir: " + (motivo == null ? "" : motivo.trim()));
    }

    private void avisar(Long idCotizacion, TipoAviso tipo, String titulo, String mensaje) {
        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion).orElse(null);
        if (cotizacion == null || cotizacion.getUserCreate() == null || cotizacion.getUserCreate().equals(quienDecide())) {
            return;
        }
        usuarioRepository.findByNombreUsuario(cotizacion.getUserCreate()).ifPresent(creador ->
                notificaciones.avisarUsuario(creador.getIdUsuario(), tipo,
                        titulo + " " + cotizacion.getCodigoCotizacion(), mensaje, "/cotizaciones/" + idCotizacion));
    }

    private static String quienDecide() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
