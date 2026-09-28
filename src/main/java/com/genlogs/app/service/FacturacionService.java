package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CambiarEstadoFacturacionRequest;
import com.genlogs.app.dto.DetalleFacturacionRequest;
import com.genlogs.app.dto.DetalleFacturacionResponse;
import com.genlogs.app.dto.FacturacionRequest;
import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.dto.RegistrarPagoRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.DetalleFacturacion;
import com.genlogs.app.model.EstadoFacturacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.TipoComprobante;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.DetalleFacturacionRepository;
import com.genlogs.app.repository.EstadoFacturacionRepository;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.TipoComprobanteRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FacturacionService {

    private final FacturacionRepository facturacionRepository;
    private final DetalleFacturacionRepository detalleFacturacionRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final TipoComprobanteRepository tipoComprobanteRepository;
    private final EstadoFacturacionRepository estadoFacturacionRepository;
    private final CondicionPagoRepository condicionPagoRepository;
    private final MonedaRepository monedaRepository;
    private final ProductoRepository productoRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public FacturacionResponse buscarPorId(Long idFacturacion) {
        return toResponse(obtenerOLanzar(idFacturacion));
    }

    @Transactional(readOnly = true)
    public FacturacionResponse buscarPorComprobante(String serie, String numero) {
        Facturacion f = facturacionRepository.findBySerieComprobanteAndNumeroComprobante(serie, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Facturación no encontrada"));
        return toResponse(f);
    }

    @Transactional(readOnly = true)
    public List<FacturacionResponse> listarPorCliente(Long idCliente) {
        return facturacionRepository.findByCliente(idCliente)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DetalleFacturacionResponse> listarDetalle(Long idFacturacion) {
        return detalleFacturacionRepository.findByFacturacion(idFacturacion).stream()
                .map(this::toDetalleResponse)
                .toList();
    }

    @Transactional
    public FacturacionResponse crear(FacturacionRequest request) {
        OrdenCompra ordenCompra = ordenCompraRepository.findById(request.getIdOrdenCompra())
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra no encontrada"));

        TipoComprobante tipoComprobante = tipoComprobanteRepository.findById(request.getIdTipoComprobante())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de comprobante no encontrado"));

        CondicionPago condicionPago = condicionPagoRepository.findById(request.getIdCondicionPago())
                .orElseThrow(() -> new ResourceNotFoundException("Condición de pago no encontrada"));

        Moneda moneda = monedaRepository.findById(request.getIdMoneda())
                .orElseThrow(() -> new ResourceNotFoundException("Moneda no encontrada"));

        if (facturacionRepository.findBySerieComprobanteAndNumeroComprobante(
                request.getSerieComprobante(), request.getNumeroComprobante()).isPresent()) {
            throw new BusinessException("Ya existe una facturación con esa serie y número de comprobante");
        }

        EstadoFacturacion estadoInicial = estadoFacturacionRepository.findByCodigoEstado("EMITIDA")
                .orElseThrow(() -> new BusinessException("No existe el estado EMITIDA configurado"));

        Usuario usuario = usuarioActual();

        Facturacion facturacion = Facturacion.builder()
                .ordenCompra(ordenCompra)
                .tipoComprobante(tipoComprobante)
                .estadoFacturacion(estadoInicial)
                .condicionPago(condicionPago)
                .moneda(moneda)
                .serieComprobante(request.getSerieComprobante())
                .numeroComprobante(request.getNumeroComprobante())
                .fechaEmision(request.getFechaEmision() != null ? request.getFechaEmision() : LocalDate.now())
                .fechaVencimiento(request.getFechaVencimiento() != null
                        ? request.getFechaVencimiento()
                        : LocalDate.now().plusDays(condicionPago.getDiasCredito() != null ? condicionPago.getDiasCredito() : 0))
                .montoPagado(BigDecimal.ZERO)
                .build();
        facturacion.setUserCreate(usuario.getNombreUsuario());
        facturacion.setProcessCreate("ALTA_FACTURACION");

        facturacion = facturacionRepository.save(facturacion);

        for (DetalleFacturacionRequest lineaReq : request.getLineas()) {
            DetalleFacturacion linea = construirLinea(facturacion, lineaReq, usuario.getNombreUsuario());
            detalleFacturacionRepository.save(linea);
        }

        return toResponse(facturacion);
    }

    @Transactional
    public FacturacionResponse cambiarEstado(Long idFacturacion, CambiarEstadoFacturacionRequest request) {
        Facturacion facturacion = obtenerOLanzar(idFacturacion);

        EstadoFacturacion nuevoEstado = estadoFacturacionRepository.findByCodigoEstado(request.getCodigoEstado())
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + request.getCodigoEstado()));

        if (Boolean.TRUE.equals(facturacion.getEstadoFacturacion().getEsFinal())) {
            throw new BusinessException("No se puede cambiar el estado de una facturación ya finalizada");
        }

        facturacion.setEstadoFacturacion(nuevoEstado);
        actualizarAuditoria(facturacion, "CAMBIO_ESTADO_FACTURACION");

        facturacion = facturacionRepository.save(facturacion);
        return toResponse(facturacion);
    }

    @Transactional
    public FacturacionResponse registrarPago(Long idFacturacion, RegistrarPagoRequest request) {
        Facturacion facturacion = obtenerOLanzar(idFacturacion);

        BigDecimal total = calcularTotal(facturacion.getIdFacturacion());
        BigDecimal nuevoPagado = facturacion.getMontoPagado().add(request.getMontoPagado());

        if (nuevoPagado.compareTo(total) > 0) {
            throw new BusinessException("El monto pagado no puede superar el total de la facturación (" + total + ")");
        }

        facturacion.setMontoPagado(nuevoPagado);

        if (nuevoPagado.compareTo(total) == 0) {
            estadoFacturacionRepository.findByCodigoEstado("PAGADA")
                    .ifPresent(facturacion::setEstadoFacturacion);
        } else {
            estadoFacturacionRepository.findByCodigoEstado("PARCIAL")
                    .ifPresent(facturacion::setEstadoFacturacion);
        }

        actualizarAuditoria(facturacion, "REGISTRO_PAGO_FACTURACION");

        facturacion = facturacionRepository.save(facturacion);
        return toResponse(facturacion);
    }

    // ------------------------------------------------------------------

    private DetalleFacturacion construirLinea(Facturacion facturacion, DetalleFacturacionRequest req, String usuario) {
        if ((req.getIdProducto() == null) == (req.getIdServicio() == null)) {
            throw new BusinessException("Cada línea debe tener producto O servicio, no ambos ni ninguno");
        }

        DetalleFacturacion.DetalleFacturacionBuilder builder = DetalleFacturacion.builder()
                .facturacion(facturacion)
                .descripcionPersonalizada(req.getDescripcionPersonalizada())
                .cantidad(req.getCantidad())
                .precioUnitario(req.getPrecioUnitario())
                .descuentoUnitario(req.getDescuentoUnitario() != null ? req.getDescuentoUnitario() : BigDecimal.ZERO);

        if (req.getIdProducto() != null) {
            builder.producto(productoRepository.findById(req.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado")));
        } else {
            builder.servicio(servicioRepository.findById(req.getIdServicio())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado")));
        }

        DetalleFacturacion linea = builder.build();
        linea.setUserCreate(usuario);
        linea.setProcessCreate("ALTA_LINEA_FACTURACION");
        return linea;
    }

    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");

    /** Suma cada línea con la misma fórmula del GENERATED de la BD (sin IGV). */
    private BigDecimal calcularSubtotal(Long idFacturacion) {
        return detalleFacturacionRepository.findByFacturacion(idFacturacion).stream()
                .map(d -> d.getCantidad()
                        .multiply(d.getPrecioUnitario().subtract(d.getDescuentoUnitario()))
                        .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total con IGV 18%, igual que vw_facturacion_totales.total
     * (la tabla "facturacion" no guarda el total, solo monto_pagado).
     */
    private BigDecimal calcularTotal(Long idFacturacion) {
        return calcularSubtotal(idFacturacion).multiply(FACTOR_IGV).setScale(2, RoundingMode.HALF_UP);
    }

    private void actualizarAuditoria(Facturacion facturacion, String proceso) {
        Usuario usuario = usuarioActual();
        facturacion.setUserUpdate(usuario.getNombreUsuario());
        facturacion.setProcessUpdate(proceso);
        facturacion.setDateUpdate(LocalDateTime.now());
    }

    private Facturacion obtenerOLanzar(Long idFacturacion) {
        return facturacionRepository.findById(idFacturacion)
                .orElseThrow(() -> new ResourceNotFoundException("Facturación no encontrada"));
    }

    private Usuario usuarioActual() {
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private FacturacionResponse toResponse(Facturacion f) {
        BigDecimal total = calcularTotal(f.getIdFacturacion());
        return FacturacionResponse.builder()
                .idFacturacion(f.getIdFacturacion())
                .idOrdenCompra(f.getOrdenCompra().getIdOrdenCompra())
                .numeroOrdenCompra(f.getOrdenCompra().getNumeroOrdenCompra())
                .numeroComprobante(f.getSerieComprobante() + "-" + f.getNumeroComprobante())
                .tipoComprobante(f.getTipoComprobante().getNombreTipo())
                .clienteRazonSocial(f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial())
                .fechaEmision(f.getFechaEmision())
                .fechaVencimiento(f.getFechaVencimiento())
                .total(total)
                .montoPagado(f.getMontoPagado())
                .saldoPendiente(total.subtract(f.getMontoPagado()))
                .moneda(f.getMoneda().getSimbolo())
                .estado(f.getEstadoFacturacion().getNombreEstado())
                .build();
    }

    private DetalleFacturacionResponse toDetalleResponse(DetalleFacturacion d) {
        return DetalleFacturacionResponse.builder()
                .idDetalleFacturacion(d.getIdDetalleFacturacion())
                .idProducto(d.getProducto() != null ? d.getProducto().getIdProducto() : null)
                .productoNombre(d.getProducto() != null ? d.getProducto().getNombreProducto() : null)
                .idServicio(d.getServicio() != null ? d.getServicio().getIdServicio() : null)
                .servicioNombre(d.getServicio() != null ? d.getServicio().getNombreServicio() : null)
                .descripcionPersonalizada(d.getDescripcionPersonalizada())
                .cantidad(d.getCantidad())
                .precioUnitario(d.getPrecioUnitario())
                .descuentoUnitario(d.getDescuentoUnitario())
                .importeLinea(d.getCantidad().multiply(d.getPrecioUnitario().subtract(d.getDescuentoUnitario()))
                        .setScale(2, RoundingMode.HALF_UP))
                .build();
    }
}
