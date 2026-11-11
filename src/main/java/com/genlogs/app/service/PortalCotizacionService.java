package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.DecisionPortalRequest;
import com.genlogs.app.dto.PortalCotizacionResponse;
import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortalCotizacionService {

    private static final String NO_ENCONTRADA = "Cotización no encontrada";
    private static final String YA_RESUELTA = "Esta cotización ya no se puede aprobar ni rechazar";
    private static final String VENCIDA = "La cotización está vencida";

    private static final Set<String> OCULTAS = Set.of(
            CodigoEstadoCotizacion.BORRADOR.name(),
            CodigoEstadoCotizacion.PENDIENTE_APROBACION.name(),
            CodigoEstadoCotizacion.APROBADA_INTERNA.name());

    private final UsuarioRepository usuarioRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionDetalleRepository cotizacionDetalleRepository;
    private final EstadoCotizacionRepository estadoCotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;

    @Transactional(readOnly = true)
    public List<PortalCotizacionResponse> listar() {
        Long idCliente = exigirCliente().getCliente().getIdCliente();
        return cotizacionRepository.findByCliente(idCliente).stream()
                .filter(c -> !oculta(c))
                .map(c -> aRespuesta(c, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public PortalCotizacionResponse detalle(Long id) {
        return aRespuesta(visible(id), true);
    }

    @Transactional
    public PortalCotizacionResponse aprobar(Long id, DecisionPortalRequest request) {
        return decidir(id, CodigoEstadoCotizacion.APROBADA, "APROBACION_PORTAL", texto(request, "Aprobada por el cliente"));
    }

    @Transactional
    public PortalCotizacionResponse rechazar(Long id, DecisionPortalRequest request) {
        return decidir(id, CodigoEstadoCotizacion.RECHAZADA, "RECHAZO_PORTAL", texto(request, "Rechazada por el cliente"));
    }

    private PortalCotizacionResponse decidir(Long id, CodigoEstadoCotizacion destino, String proceso, String comentario) {
        Usuario usuario = exigirCliente();
        Cotizacion cotizacion = visible(id, usuario.getCliente().getIdCliente());
        exigirDecidible(cotizacion);
        EstadoCotizacion estado = estadoCotizacionRepository.findByCodigoEstado(destino.name())
                .orElseThrow(() -> new BusinessException("No existe el estado " + destino.name() + " configurado"));
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setUserUpdate(usuario.getNombreUsuario());
        cotizacion.setProcessUpdate(proceso);
        cotizacion.setDateUpdate(LocalDateTime.now());
        cotizacionRepository.save(cotizacion);
        seguimiento(cotizacion, estado, usuario, comentario);
        return aRespuesta(cotizacion, true);
    }

    private void exigirDecidible(Cotizacion cotizacion) {
        if (cotizacion.getFechaValidez() != null && cotizacion.getFechaValidez().isBefore(LocalDate.now())) {
            throw new BusinessException(VENCIDA);
        }
        String codigo = cotizacion.getEstadoCotizacion().getCodigoEstado();
        boolean abierta = CodigoEstadoCotizacion.ENVIADA.name().equals(codigo)
                || CodigoEstadoCotizacion.EN_NEGOCIACION.name().equals(codigo);
        if (!abierta) {
            throw new BusinessException(YA_RESUELTA);
        }
    }

    private Cotizacion visible(Long id) {
        return visible(id, exigirCliente().getCliente().getIdCliente());
    }

    /** Otra empresa, borrador o aprobación interna: el mismo 404, sin decir si existe. */
    private Cotizacion visible(Long id, Long idCliente) {
        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NO_ENCONTRADA));
        Long dueno = cotizacion.getCliente() != null ? cotizacion.getCliente().getIdCliente() : null;
        if (!idCliente.equals(dueno) || oculta(cotizacion) || !"A".equals(cotizacion.getStatus())) {
            throw new ResourceNotFoundException(NO_ENCONTRADA);
        }
        return cotizacion;
    }

    private static boolean oculta(Cotizacion cotizacion) {
        return cotizacion.getEstadoCotizacion() != null
                && OCULTAS.contains(cotizacion.getEstadoCotizacion().getCodigoEstado());
    }

    private Usuario exigirCliente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        Usuario usuario = usuarioRepository.findWithClienteByNombreUsuario(auth.getName()).orElse(null);
        if (usuario == null || usuario.getCliente() == null) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        return usuario;
    }

    private void seguimiento(Cotizacion cotizacion, EstadoCotizacion estado, Usuario usuario, String comentario) {
        SeguimientoCotizacion seguimiento = SeguimientoCotizacion.builder()
                .cotizacion(cotizacion)
                .estadoCotizacion(estado)
                .usuario(usuario)
                .fechaEvento(LocalDateTime.now())
                .comentario(comentario)
                .build();
        seguimiento.setUserCreate(usuario.getNombreUsuario());
        seguimiento.setProcessCreate("PORTAL_COTIZACION");
        seguimientoCotizacionRepository.save(seguimiento);
    }

    private static String texto(DecisionPortalRequest request, String base) {
        if (request == null || request.getMotivo() == null || request.getMotivo().isBlank()) {
            return base;
        }
        return base + ". " + request.getMotivo().trim();
    }

    private PortalCotizacionResponse aRespuesta(Cotizacion cotizacion, boolean conLineas) {
        BigDecimal total = cotizacionRepository.findTotalesByCotizacion(cotizacion.getIdCotizacion())
                .map(CotizacionRepository.TotalesCotizacionProjection::getTotal)
                .orElse(BigDecimal.ZERO);
        PortalCotizacionResponse.PortalCotizacionResponseBuilder builder = PortalCotizacionResponse.builder()
                .id(cotizacion.getIdCotizacion())
                .codigo(cotizacion.getCodigoCotizacion())
                .fechaCotizacion(cotizacion.getFechaCotizacion())
                .fechaValidez(cotizacion.getFechaValidez())
                .estado(cotizacion.getEstadoCotizacion() != null
                        ? cotizacion.getEstadoCotizacion().getNombreEstado() : null)
                .total(total)
                .moneda(cotizacion.getMoneda() != null ? cotizacion.getMoneda().getCodigoMoneda() : null);
        if (conLineas) {
            builder.lineas(cotizacionDetalleRepository.findByCotizacion(cotizacion.getIdCotizacion()).stream()
                    .map(PortalCotizacionService::linea)
                    .toList());
        }
        return builder.build();
    }

    private static PortalCotizacionResponse.Linea linea(CotizacionDetalle detalle) {
        return PortalCotizacionResponse.Linea.builder()
                .descripcion(detalle.getDescripcionPersonalizada())
                .cantidad(detalle.getCantidad())
                .unidad(detalle.getUnidadMedida() != null ? detalle.getUnidadMedida().getNombreUnidad() : null)
                .precioUnitario(detalle.getPrecioUnitario())
                .importe(detalle.getImporteLinea())
                .build();
    }
}
