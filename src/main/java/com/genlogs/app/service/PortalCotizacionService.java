package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.Clock;
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
import com.genlogs.app.repository.OrdenCompraRepository;
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
    private final OrdenCompraRepository ordenCompraRepository;
    private Clock clock = Clock.system(PlazoDespacho.LIMA);

    /** Solo para pruebas. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    static LocalDate hoyLima(Clock clock) {
        return LocalDate.now(clock.withZone(PlazoDespacho.LIMA));
    }

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
        return decidir(id, CodigoEstadoCotizacion.ACEPTADA_CLIENTE, "APROBACION_PORTAL", texto(request, "Aceptada por el cliente"));
    }

    @Transactional
    public PortalCotizacionResponse rechazar(Long id, DecisionPortalRequest request) {
        return decidir(id, CodigoEstadoCotizacion.RECHAZADA, "RECHAZO_PORTAL", texto(request, "Rechazada por el cliente"));
    }

    private PortalCotizacionResponse decidir(Long id, CodigoEstadoCotizacion destino, String proceso, String comentario) {
        Usuario usuario = exigirCliente();
        Cotizacion cotizacion = visible(id, usuario.getCliente().getIdCliente());
        aplicarDecisionCliente(cotizacion, destino, usuario, comentario, proceso);
        return aRespuesta(cotizacion, true);
    }

    /**
     * El mismo cambio de estado que aprobar o rechazar en el portal:
     * solo ENVIADA o EN_NEGOCIACION, y el día de fechaValidez sigue abierto.
     */
    void aplicarDecisionCliente(
            Cotizacion cotizacion,
            CodigoEstadoCotizacion destino,
            Usuario usuario,
            String comentario,
            String proceso) {
        exigirDecidible(cotizacion);
        EstadoCotizacion estado = estadoCotizacionRepository.findByCodigoEstado(destino.name())
                .orElseThrow(() -> new BusinessException("No existe el estado " + destino.name() + " configurado"));
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setUserUpdate(usuario.getNombreUsuario());
        cotizacion.setProcessUpdate(proceso);
        cotizacion.setDateUpdate(LocalDateTime.now());
        cotizacionRepository.save(cotizacion);
        seguimiento(cotizacion, estado, usuario, comentario, proceso);
    }

    private void exigirDecidible(Cotizacion cotizacion) {
        LocalDate hoy = hoyLima(clock);
        if (puedeDecidir(cotizacion, hoy)) {
            return;
        }
        if (!fechaPermiteDecision(cotizacion, hoy)) {
            throw new BusinessException(VENCIDA);
        }
        throw new BusinessException(YA_RESUELTA);
    }

    /** ENVIADA o EN_NEGOCIACION, y fechaValidez nula o no anterior a hoy en Lima. */
    static boolean puedeDecidir(Cotizacion cotizacion) {
        return puedeDecidir(cotizacion, LocalDate.now(PlazoDespacho.LIMA));
    }

    /** fechaValidez inclusive: el día de validez se puede decidir; el siguiente, no. */
    static boolean puedeDecidir(Cotizacion cotizacion, LocalDate hoy) {
        return estadoPermiteDecision(cotizacion) && fechaPermiteDecision(cotizacion, hoy);
    }

    private static boolean estadoPermiteDecision(Cotizacion cotizacion) {
        String codigo = cotizacion.getEstadoCotizacion() == null
                ? null
                : cotizacion.getEstadoCotizacion().getCodigoEstado();
        return CodigoEstadoCotizacion.ENVIADA.name().equals(codigo)
                || CodigoEstadoCotizacion.EN_NEGOCIACION.name().equals(codigo);
    }

    private static boolean fechaPermiteDecision(Cotizacion cotizacion, LocalDate hoy) {
        LocalDate fecha = cotizacion.getFechaValidez();
        return fecha == null || !fecha.isBefore(hoy);
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

    private void seguimiento(
            Cotizacion cotizacion, EstadoCotizacion estado, Usuario usuario, String comentario, String proceso) {
        SeguimientoCotizacion seguimiento = SeguimientoCotizacion.builder()
                .cotizacion(cotizacion)
                .estadoCotizacion(estado)
                .usuario(usuario)
                .fechaEvento(LocalDateTime.now())
                .comentario(comentario)
                .build();
        seguimiento.setUserCreate(usuario.getNombreUsuario());
        seguimiento.setProcessCreate(proceso);
        seguimientoCotizacionRepository.save(seguimiento);
    }

    private static String texto(DecisionPortalRequest request, String base) {
        if (request == null || request.getMotivo() == null || request.getMotivo().isBlank()) {
            return base;
        }
        return base + ". " + request.getMotivo().trim();
    }

    private PortalCotizacionResponse aRespuesta(Cotizacion cotizacion, boolean conLineas) {
        CotizacionRepository.TotalesCotizacionProjection totales = cotizacionRepository
                .findTotalesByCotizacion(cotizacion.getIdCotizacion())
                .orElse(null);
        BigDecimal subtotal = totales == null ? BigDecimal.ZERO : totales.getSubtotal();
        BigDecimal igv = totales == null ? BigDecimal.ZERO : totales.getIgv();
        BigDecimal total = totales == null ? BigDecimal.ZERO : totales.getTotal();
        PortalCotizacionResponse.PortalCotizacionResponseBuilder builder = PortalCotizacionResponse.builder()
                .id(cotizacion.getIdCotizacion())
                .codigo(cotizacion.getCodigoCotizacion())
                .fechaCotizacion(cotizacion.getFechaCotizacion())
                .fechaValidez(cotizacion.getFechaValidez())
                .estado(cotizacion.getEstadoCotizacion() != null
                        ? cotizacion.getEstadoCotizacion().getNombreEstado() : null)
                .codigoEstado(cotizacion.getEstadoCotizacion() != null
                        ? cotizacion.getEstadoCotizacion().getCodigoEstado() : null)
                .puedeDecidir(puedeDecidir(cotizacion))
                .subtotal(subtotal)
                .igv(igv)
                .total(total)
                .moneda(cotizacion.getMoneda() != null ? cotizacion.getMoneda().getCodigoMoneda() : null)
                .numeroOcCliente(numeroOc(cotizacion.getIdCotizacion()))
                .motivoAnulacion(motivoAnulacion(cotizacion));
        if (conLineas) {
            builder.lineas(cotizacionDetalleRepository.findByCotizacion(cotizacion.getIdCotizacion()).stream()
                    .map(PortalCotizacionService::linea)
                    .toList());
        }
        return builder.build();
    }

    /** El comentario con el que se pasó a ANULADA. En otro estado no va. */
    private String motivoAnulacion(Cotizacion cotizacion) {
        String codigo = cotizacion.getEstadoCotizacion() == null
                ? null
                : cotizacion.getEstadoCotizacion().getCodigoEstado();
        if (!CodigoEstadoCotizacion.ANULADA.name().equals(codigo) || cotizacion.getIdCotizacion() == null) {
            return null;
        }
        return seguimientoCotizacionRepository.findByCotizacion(cotizacion.getIdCotizacion()).stream()
                .filter(s -> s.getEstadoCotizacion() != null
                        && CodigoEstadoCotizacion.ANULADA.name().equals(s.getEstadoCotizacion().getCodigoEstado()))
                .map(SeguimientoCotizacion::getComentario)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String numeroOc(Long idCotizacion) {
        if (idCotizacion == null) {
            return null;
        }
        List<String> numeros = ordenCompraRepository.numerosOcCliente(idCotizacion);
        if (numeros == null || numeros.isEmpty()) {
            return null;
        }
        String numero = numeros.get(0);
        return numero == null || numero.isBlank() ? null : numero;
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
