package com.genlogs.app.service;

import java.time.LocalDateTime;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Aviso y seguimiento cuando la cotización ya quedó APROBADA y la orden no se creó.
 * Cada aviso va en la transacción de {@code avisarRol}. El seguimiento va en otra.
 */
@Component
public class AvisoOrdenNoCreada {

    static final String FRASE = "la orden de compra no se creó y debe registrarse a mano";
    private static final String TITULO = "Orden de compra no creada";

    private final CotizacionRepository cotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public AvisoOrdenNoCreada(
            CotizacionRepository cotizacionRepository,
            SeguimientoCotizacionRepository seguimientoCotizacionRepository,
            UsuarioRepository usuarioRepository,
            NotificacionService notificacionService) {
        this.cotizacionRepository = cotizacionRepository;
        this.seguimientoCotizacionRepository = seguimientoCotizacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * Avisa a los dos roles. Si uno falla, igual intenta el otro y luego lanza,
     * para que el llamador deje el seguimiento.
     */
    public void avisar(Long idCotizacion) {
        String mensaje = mensaje(cotizacion(idCotizacion));
        String enlace = "/cotizaciones/" + idCotizacion;
        RuntimeException fallo = null;
        fallo = intentar(fallo, () -> notificacionService.avisarRol(
                RolSistema.ASISTENTE_ADMINISTRATIVO, TipoAviso.ORDEN_NO_CREADA,
                TITULO, mensaje, enlace, false));
        fallo = intentar(fallo, () -> notificacionService.avisarRol(
                RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.ORDEN_NO_CREADA,
                TITULO, mensaje, enlace, true));
        if (fallo != null) {
            throw fallo;
        }
    }

    /** Otra transacción: el motivo queda en el historial aunque el aviso haya fallado. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSeguimiento(Long idCotizacion, String motivo) {
        Cotizacion cotizacion = cotizacion(idCotizacion);
        Usuario usuario = usuarioActual();
        String autor = usuario != null ? usuario.getNombreUsuario() : "sistema";
        SeguimientoCotizacion seguimiento = SeguimientoCotizacion.builder()
                .cotizacion(cotizacion)
                .estadoCotizacion(cotizacion.getEstadoCotizacion())
                .usuario(usuario)
                .fechaEvento(LocalDateTime.now())
                .comentario(FRASE + ". Motivo: " + (motivo == null || motivo.isBlank() ? "sin detalle" : motivo.trim()))
                .build();
        seguimiento.setUserCreate(autor);
        seguimiento.setProcessCreate("FALLO_ORDEN_AUTOMATICA");
        seguimientoCotizacionRepository.save(seguimiento);
    }

    private Cotizacion cotizacion(Long idCotizacion) {
        return cotizacionRepository.findConClienteParaAviso(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
    }

    private static String mensaje(Cotizacion cotizacion) {
        String codigo = cotizacion.getCodigoCotizacion() != null
                ? cotizacion.getCodigoCotizacion()
                : String.valueOf(cotizacion.getIdCotizacion());
        String empresa = "sin empresa";
        if (cotizacion.getCliente() != null && cotizacion.getCliente().getTercero() != null
                && cotizacion.getCliente().getTercero().getRazonSocial() != null
                && !cotizacion.getCliente().getTercero().getRazonSocial().isBlank()) {
            empresa = cotizacion.getCliente().getTercero().getRazonSocial().trim();
        }
        return codigo + " de " + empresa + ": " + FRASE + ".";
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null || auth.getName().isBlank()) {
            return null;
        }
        return usuarioRepository.findByNombreUsuario(auth.getName()).orElse(null);
    }

    private static RuntimeException intentar(RuntimeException previo, Runnable aviso) {
        try {
            aviso.run();
            return previo;
        } catch (RuntimeException e) {
            return previo != null ? previo : e;
        }
    }
}
