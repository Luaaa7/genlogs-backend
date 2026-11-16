package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.RespuestaClienteRequest;
import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Registro del Jefe cuando el cliente responde fuera del portal.
 * Aprobar y rechazar usan la misma decisión que el portal.
 */
@Service
@RequiredArgsConstructor
public class RespuestaClienteService {

    static final String SOLO_EL_JEFE =
            "Solo el Jefe Administrativo registra la respuesta del cliente";
    static final String COMENTARIO_OBLIGATORIO =
            "Indica cómo y cuándo respondió el cliente";
    static final String ADJUNTO_OBLIGATORIO =
            "Adjunta el correo o la orden de compra de la minera";
    static final String DESTINO_NO_ADMITIDO =
            "La respuesta del cliente solo puede ser aprobada o rechazada";

    private static final String JEFE = "ROLE_" + RolSistema.JEFE_ADMINISTRATIVO.name();
    private static final String PROCESO = "RESPUESTA_FUERA_PORTAL";

    private final CotizacionRepository cotizacionRepository;
    private final AdjuntoCotizacionRepository adjuntoCotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PortalCotizacionService portalCotizacionService;

    /**
     * @return APROBADA o RECHAZADA, ya guardada. La orden la crea el controlador
     *         con {@link CotizacionService#registrarOrdenSiAprobada}, igual que el portal.
     */
    @Transactional
    public String registrar(Long idCotizacion, RespuestaClienteRequest request) {
        Usuario jefe = exigirJefe();
        String comentario = comentario(request);
        CodigoEstadoCotizacion destino = destino(request);
        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        AdjuntoCotizacion adjunto = exigirAdjuntoNuevo(cotizacion, request == null ? null : request.getIdAdjunto());
        String conArchivo = comentario + " (adjunto: " + adjunto.getNombreArchivo() + ")";
        portalCotizacionService.aplicarDecisionCliente(cotizacion, destino, jefe, conArchivo, PROCESO);
        return destino.name();
    }

    private Usuario exigirJefe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean jefe = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> JEFE.equals(a.getAuthority()));
        if (!jefe || auth.getName() == null || auth.getName().isBlank()) {
            throw new AccessDeniedException(SOLO_EL_JEFE);
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new AccessDeniedException(SOLO_EL_JEFE));
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
        if (CodigoEstadoCotizacion.APROBADA.name().equalsIgnoreCase(codigo)) {
            return CodigoEstadoCotizacion.APROBADA;
        }
        if (CodigoEstadoCotizacion.RECHAZADA.name().equalsIgnoreCase(codigo)) {
            return CodigoEstadoCotizacion.RECHAZADA;
        }
        throw new BusinessException(DESTINO_NO_ADMITIDO);
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
