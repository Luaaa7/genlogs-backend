package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
     * GET /{id}/pdf — antes 404 garantizado: facturacionApi.descargarPdf()
     * del frontend llama a esta ruta y el controller no tenía nada mapeado.
     * Mismo patrón PDFBox que CotizacionService.descargarPdf / ReporteService.
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long idFacturacion) {
        Facturacion f = obtenerOLanzar(idFacturacion);
        List<DetalleFacturacion> lineas = detalleFacturacionRepository.findByFacturacion(idFacturacion);
        BigDecimal subtotal = calcularSubtotal(idFacturacion);
        BigDecimal total = calcularTotal(idFacturacion);
        BigDecimal igv = total.subtract(subtotal);
        String simbolo = f.getMoneda().getSimbolo();

        String[] encabezados = { "Descripción", "Cant.", "P. Unit.", "Importe" };
        String[][] datos = new String[lineas.size()][];
        for (int i = 0; i < lineas.size(); i++) {
            DetalleFacturacion d = lineas.get(i);
            String descripcion = d.getProducto() != null ? d.getProducto().getNombreProducto()
                    : d.getServicio() != null ? d.getServicio().getNombreServicio()
                    : d.getDescripcionPersonalizada() != null ? d.getDescripcionPersonalizada() : "—";
            BigDecimal importe = d.getCantidad().multiply(d.getPrecioUnitario().subtract(d.getDescuentoUnitario()))
                    .setScale(2, RoundingMode.HALF_UP);
            datos[i] = new String[] {
                    descripcion,
                    d.getCantidad().toPlainString(),
                    d.getPrecioUnitario().toPlainString(),
                    importe.toPlainString(),
            };
        }

        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margenIzq = 40;
            float anchoUtil = PDRectangle.A4.getWidth() - 2 * margenIzq;
            float anchoColumna = anchoUtil / encabezados.length;
            float alturaLinea = 16;
            float yInicio = PDRectangle.A4.getHeight() - 50;

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);
            float y = yInicio;

            String numeroComprobante = f.getSerieComprobante() + "-" + f.getNumeroComprobante();

            cs.setFont(fuenteTitulo, 14);
            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText(f.getTipoComprobante().getNombreTipo() + " " + numeroComprobante);
            cs.endText();
            y -= alturaLinea * 1.4f;

            cs.setFont(fuenteTexto, 10);
            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText("Cliente: " + f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial());
            cs.endText();
            y -= alturaLinea;

            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText("Emisión: " + f.getFechaEmision().format(FMT_FECHA)
                    + "   Vencimiento: " + f.getFechaVencimiento().format(FMT_FECHA));
            cs.endText();
            y -= alturaLinea * 1.8f;

            y = escribirFilaPdf(cs, fuenteTitulo, encabezados, margenIzq, y, anchoColumna, alturaLinea);

            for (String[] fila : datos) {
                if (y < 100) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = yInicio;
                }
                y = escribirFilaPdf(cs, fuenteTexto, fila, margenIzq, y, anchoColumna, alturaLinea);
            }

            y -= alturaLinea;
            cs.setFont(fuenteTexto, 10);
            for (String linea : List.of(
                    "Subtotal: " + simbolo + " " + subtotal.toPlainString(),
                    "IGV (18%): " + simbolo + " " + igv.toPlainString(),
                    "Pagado: " + simbolo + " " + f.getMontoPagado().toPlainString())) {
                if (y < 50) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = yInicio;
                }
                cs.beginText();
                cs.newLineAtOffset(PDRectangle.A4.getWidth() - margenIzq - 220, y);
                cs.showText(linea);
                cs.endText();
                y -= alturaLinea;
            }
            cs.setFont(fuenteTitulo, 11);
            cs.beginText();
            cs.newLineAtOffset(PDRectangle.A4.getWidth() - margenIzq - 220, y);
            cs.showText("Total: " + simbolo + " " + total.toPlainString());
            cs.endText();

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la facturación", e);
        }
    }

    private float escribirFilaPdf(PDPageContentStream cs, PDType1Font fuente, String[] valores,
            float margenIzq, float y, float anchoColumna, float alturaLinea) throws IOException {
        cs.setFont(fuente, 9);
        float x = margenIzq;
        for (String valor : valores) {
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(valor == null ? "" : valor);
            cs.endText();
            x += anchoColumna;
        }
        return y - alturaLinea;
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
                .codigoComprobante(f.getSerieComprobante() + "-" + f.getNumeroComprobante())
                .cliente(f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial())
                .estadoCodigo(f.getEstadoFacturacion().getCodigoEstado())
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
