package com.genlogs.app.service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.genlogs.app.model.Tercero;
import com.genlogs.app.service.pdf.PdfGenerationService;
import com.genlogs.app.util.PdfUtils;
import com.genlogs.app.service.pdf.PdfGenerationService.FacturacionPdfData;
import com.genlogs.app.service.pdf.PdfGenerationService.LineaPdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CambiarEstadoFacturacionRequest;
import com.genlogs.app.dto.DetalleFacturacionRequest;
import com.genlogs.app.dto.DetalleFacturacionResponse;
import com.genlogs.app.dto.FacturacionRequest;
import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.dto.PaginaResponse;
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
    private final PdfGenerationService pdfGenerationService;

    private static final Logger log = LoggerFactory.getLogger(FacturacionService.class);

    @Transactional(readOnly = true)
    public PaginaResponse<FacturacionResponse> listar(String estadoCodigo, String tipoCodigo, Long idOrdenCompra,
                                                      int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        Page<Facturacion> resultado = facturacionRepository.buscar(
                estadoCodigo == null ? "" : estadoCodigo.trim(),
                tipoCodigo == null ? "" : tipoCodigo.trim(),
                idOrdenCompra == null ? 0L : idOrdenCompra,
                PageRequest.of(p, s));
        return PaginaResponse.of(resultado.map(this::toResponse));
    }

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
        // La tabla no tiene un detalle de pagos: cada pago queda anotado en las observaciones.
        facturacion.setObservaciones(agregarNotaPago(facturacion, request));

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

    /**
     * GET /{id}/pdf — genera PDF delegando a {@link PdfGenerationService}.
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long idFacturacion) {
        Facturacion f = obtenerOLanzar(idFacturacion);
        List<DetalleFacturacion> lineas = detalleFacturacionRepository.findByFacturacion(idFacturacion);
        BigDecimal subtotal = calcularSubtotal(idFacturacion);
        BigDecimal total = calcularTotal(idFacturacion);
        BigDecimal igv = total.subtract(subtotal);
        BigDecimal pagado = f.getMontoPagado() != null ? f.getMontoPagado() : BigDecimal.ZERO;

        String moneda = PdfUtils.codigoMoneda(f.getMoneda());

        // Misma tabla que la cotización: título y debajo marca, descripción y código.
        List<LineaPdf> filas = new ArrayList<>();
        for (DetalleFacturacion d : lineas) {
            filas.add(PdfGenerationService.linea(
                    d.getProducto(),
                    d.getServicio(),
                    null, // el detalle de factura no guarda unidad: se usa la del producto/servicio
                    d.getDescripcionPersonalizada(),
                    d.getCantidad(),
                    d.getPrecioUnitario(),
                    d.getDescuentoUnitario(),
                    null, // importe = cantidad x (precio - descuento)
                    moneda));
        }

        String numeroComprobante = (f.getSerieComprobante() != null ? f.getSerieComprobante() : "") + "-" + (f.getNumeroComprobante() != null ? f.getNumeroComprobante() : "");
        String tipoComprobante = f.getTipoComprobante() != null ? f.getTipoComprobante().getNombreTipo() : "Comprobante";

        OrdenCompra oc = f.getOrdenCompra();
        Tercero tercero = oc != null && oc.getCotizacion() != null
                && oc.getCotizacion().getCliente() != null
                ? oc.getCotizacion().getCliente().getTercero()
                : null;

        FacturacionPdfData data = new FacturacionPdfData(
                tipoComprobante,
                numeroComprobante,
                f.getFechaEmision(),
                f.getFechaVencimiento(),
                obtenerNombreCliente(f),
                PdfUtils.documentoTercero(tercero),
                PdfUtils.direccionTercero(tercero),
                oc != null ? oc.getNumeroOrdenCompra() : null,
                oc != null && oc.getCotizacion() != null ? oc.getCotizacion().getCodigoCotizacion() : null,
                f.getCondicionPago() != null ? f.getCondicionPago().getNombreCondicion() : null,
                moneda,
                PdfUtils.nombreMoneda(f.getMoneda()),
                filas,
                subtotal,
                igv,
                total,
                pagado,
                f.getObservaciones()
        );

        return pdfGenerationService.generarPdfFacturacion(data);
    }

    


    private Long obtenerIdCliente(Facturacion f) {
        if (f.getOrdenCompra() == null || f.getOrdenCompra().getCotizacion() == null
                || f.getOrdenCompra().getCotizacion().getCliente() == null) {
            return null;
        }
        return f.getOrdenCompra().getCotizacion().getCliente().getIdCliente();
    }

    // Obtiene razón social o documento con fallback seguro
    private String obtenerNombreCliente(Facturacion f) {
        try {
            if (f.getOrdenCompra() != null && f.getOrdenCompra().getCotizacion() != null
                    && f.getOrdenCompra().getCotizacion().getCliente() != null
                    && f.getOrdenCompra().getCotizacion().getCliente().getTercero() != null) {
                String rs = f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial();
                if (rs != null && !rs.isBlank()) {
                    return rs;
                }
                String doc = f.getOrdenCompra().getCotizacion().getCliente().getTercero().getNumeroDocumento();
                if (doc != null && !doc.isBlank()) {
                    return doc;
                }
            }
        } catch (Exception e) {
            log.warn("Error obteniendo nombre de cliente para facturación {}: {}", f.getIdFacturacion(), e.getMessage());
        }
        return "—";
    }

    /** "Pago 24/10/2026 · Transferencia · Op. 123456 · USD 100.00" agregado a las observaciones. */
    private String agregarNotaPago(Facturacion f, RegistrarPagoRequest request) {
        StringBuilder nota = new StringBuilder("Pago ")
                .append(request.getFechaPago().format(FMT_FECHA))
                .append(" · ").append(request.getMedioPago().trim());
        if (request.getNumeroOperacion() != null && !request.getNumeroOperacion().isBlank()) {
            nota.append(" · Op. ").append(request.getNumeroOperacion().trim());
        }
        String moneda = f.getMoneda() != null ? f.getMoneda().getCodigoMoneda() : "PEN";
        nota.append(" · ").append(moneda).append(" ").append(request.getMontoPagado().setScale(2, RoundingMode.HALF_UP).toPlainString());

        String previas = f.getObservaciones();
        return (previas == null || previas.isBlank()) ? nota.toString() : previas + "\n" + nota;
    }

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
                .map(d -> {
                    // Evita NPE al calcular con valores nulos
                    BigDecimal cant = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ZERO;
                    BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
                    BigDecimal desc = d.getDescuentoUnitario() != null ? d.getDescuentoUnitario() : BigDecimal.ZERO;
                    return cant.multiply(precio.subtract(desc)).setScale(2, RoundingMode.HALF_UP);
                })
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
        BigDecimal pagado = f.getMontoPagado() != null ? f.getMontoPagado() : BigDecimal.ZERO;
        String clienteNombre = obtenerNombreCliente(f);
        return FacturacionResponse.builder()
                .idFacturacion(f.getIdFacturacion())
                .idOrdenCompra(f.getOrdenCompra() != null ? f.getOrdenCompra().getIdOrdenCompra() : null)
                .numeroOrdenCompra(f.getOrdenCompra() != null ? f.getOrdenCompra().getNumeroOrdenCompra() : null)
                .numeroComprobante(f.getSerieComprobante() + "-" + f.getNumeroComprobante())
                .codigoComprobante(f.getSerieComprobante() + "-" + f.getNumeroComprobante())
                .cliente(clienteNombre)
                .clienteId(obtenerIdCliente(f))
                .estadoCodigo(f.getEstadoFacturacion() != null ? f.getEstadoFacturacion().getCodigoEstado() : null)
                .tipoComprobante(f.getTipoComprobante() != null ? f.getTipoComprobante().getNombreTipo() : null)
                .clienteRazonSocial(clienteNombre)
                .fechaEmision(f.getFechaEmision())
                .fechaVencimiento(f.getFechaVencimiento())
                .total(total)
                .montoPagado(pagado)
                .saldoPendiente(total.subtract(pagado))
                .moneda(f.getMoneda() != null ? f.getMoneda().getSimbolo() : "S/")
                .estado(f.getEstadoFacturacion() != null ? f.getEstadoFacturacion().getNombreEstado() : null)
                .build();
    }

    private DetalleFacturacionResponse toDetalleResponse(DetalleFacturacion d) {
        BigDecimal cant = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ZERO;
        BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
        BigDecimal desc = d.getDescuentoUnitario() != null ? d.getDescuentoUnitario() : BigDecimal.ZERO;
        BigDecimal importe = cant.multiply(precio.subtract(desc)).setScale(2, RoundingMode.HALF_UP);
        return DetalleFacturacionResponse.builder()
                .idDetalleFacturacion(d.getIdDetalleFacturacion())
                .idProducto(d.getProducto() != null ? d.getProducto().getIdProducto() : null)
                .productoNombre(d.getProducto() != null ? d.getProducto().getNombreProducto() : null)
                .idServicio(d.getServicio() != null ? d.getServicio().getIdServicio() : null)
                .servicioNombre(d.getServicio() != null ? d.getServicio().getNombreServicio() : null)
                .descripcionPersonalizada(d.getDescripcionPersonalizada())
                .cantidad(cant)
                .precioUnitario(precio)
                .descuentoUnitario(desc)
                .importeLinea(importe)
                .build();
    }
}