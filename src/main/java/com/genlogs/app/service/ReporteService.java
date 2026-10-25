package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.genlogs.app.service.pdf.PdfGenerationService;
import com.genlogs.app.service.pdf.PdfGenerationService.ReportePdfData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.genlogs.app.util.PdfUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ReportePreviewResponse;
import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository.TotalesCotizacionPorIdProjection;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ServicioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Backend del módulo de Reportes (POST /api/reportes/generar). Arma el archivo
 * (Excel con Apache POI o PDF con PDFBox) para los 5 tipos de reporte que
 * el frontend ya sabe pedir.
 *
 * <p>La vista previa (POST /api/reportes/preview) usa EXACTAMENTE el mismo
 * armado de filas que la descarga: mismo tipo de reporte, mismo periodo y
 * mismos filtros. Si cambian las reglas de negocio, cambian en los dos.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Máximo de filas que se devuelven en la vista previa. El total real
     * siempre viaja en "totalFilas"; el archivo descargable NO se recorta.
     */
    private static final int MAX_FILAS_PREVIEW = 200;

    private final CotizacionRepository cotizacionRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturacionRepository facturacionRepository;
    private final ServicioRepository servicioRepository;
    private final ProductoRepository productoRepository;
    private final PdfGenerationService pdfGenerationService;

    public record ArchivoGenerado(byte[] contenido, String nombreArchivo, String contentType) {}

    /**
     * Descarga el reporte en Excel o PDF. No se modifica: la vista previa
     * reutiliza el mismo armado de datos.
     */
    @Transactional(readOnly = true)
    public ArchivoGenerado generar(ReporteRequest request) {
        validarReporte(request);

        String[][] filas = construirFilas(request);
        String[] encabezados = filas[0];
        String[][] datos = new String[filas.length - 1][];
        System.arraycopy(filas, 1, datos, 0, filas.length - 1);

        String nombreBase = "reporte-" + request.getTipoReporte().name().toLowerCase()
                + "-" + request.getFechaInicio() + "_" + request.getFechaFin();

        if (request.getFormato() == ReporteRequest.FormatoReporte.EXCEL) {
            byte[] bytes = generarExcel(encabezados, datos, request.getTipoReporte().name());
            return new ArchivoGenerado(bytes, nombreBase + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            ReportePdfData data = new ReportePdfData(request.getTipoReporte().name(), encabezados, datos);
            byte[] bytes = pdfGenerationService.generarPdfReporte(data);
            return new ArchivoGenerado(bytes, nombreBase + ".pdf", "application/pdf");
        }
    }

    /**
     * Vista previa: los mismos datos que se descargarían, en JSON, para
     * pintarlos como tabla.
     *
     * <p>No inventa datos ni captura excepciones: si algo falla, el error
     * sube al manejador global con su código correcto.
     */
    @Transactional(readOnly = true)
    public ReportePreviewResponse preview(ReporteRequest request) {
        validarReporte(request);

        String[][] filas = construirFilas(request);
        String[] encabezados = filas[0];

        int totalFilas = filas.length - 1;
        int mostradas = Math.min(totalFilas, MAX_FILAS_PREVIEW);

        List<List<String>> datos = new ArrayList<>(mostradas);
        for (int i = 1; i <= mostradas; i++) {
            datos.add(List.of(filas[i]));
        }

        return ReportePreviewResponse.builder()
                .tipoReporte(request.getTipoReporte())
                .formato(request.getFormato())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .encabezados(List.of(encabezados))
                .filas(datos)
                .totalFilas(totalFilas)
                .filasMostradas(mostradas)
                .truncado(totalFilas > mostradas)
                .build();
    }

    /** Validaciones comunes a la vista previa y a la descarga. */
    private void validarReporte(ReporteRequest request) {
        if (request == null || request.getTipoReporte() == null) {
            throw new BusinessException("Debe indicar el tipo de reporte");
        }
        if (request.getFechaInicio() == null || request.getFechaFin() == null) {
            throw new BusinessException("Debe indicar el rango de fechas del reporte");
        }
        if (request.getFechaInicio().isAfter(request.getFechaFin())) {
            throw new BusinessException("La fecha de inicio no puede ser mayor a la fecha fin");
        }
    }

    // ------------------------------------------------------------------
    // Armado de filas por tipo de reporte
    // ------------------------------------------------------------------

    private String[][] construirFilas(ReporteRequest req) {
        LocalDate desde = req.getFechaInicio();
        LocalDate hasta = req.getFechaFin();

        return switch (req.getTipoReporte()) {
            case COTIZACIONES -> filasCotizaciones(desde, hasta);
            case ORDENES_COMPRA -> filasOrdenesCompra(desde, hasta);
            case FACTURACION -> filasFacturacion(desde, hasta);
            case SERVICIOS -> filasServicios();
            case PRODUCTOS -> filasProductos();
        };
    }

    private String[][] filasCotizaciones(LocalDate desde, LocalDate hasta) {
        List<Cotizacion> cotizaciones = cotizacionRepository
                .findByStatusAndFechaCotizacionBetweenOrderByFechaCotizacionAsc("A", desde, hasta);

        // Los totales salen de la vista en UNA consulta (no una por fila).
        Map<Long, TotalesCotizacionPorIdProjection> totales = new HashMap<>();
        if (!cotizaciones.isEmpty()) {
            List<Long> ids = cotizaciones.stream().map(Cotizacion::getIdCotizacion).toList();
            for (TotalesCotizacionPorIdProjection t : cotizacionRepository.findTotalesPorCotizaciones(ids)) {
                totales.put(t.getIdCotizacion(), t);
            }
        }

        String[][] filas = new String[cotizaciones.size() + 1][];
        filas[0] = new String[] { "Código", "Cliente", "Fecha", "Estado", "Moneda", "Total" };
        for (int i = 0; i < cotizaciones.size(); i++) {
            Cotizacion c = cotizaciones.get(i);
            TotalesCotizacionPorIdProjection t = totales.get(c.getIdCotizacion());
            BigDecimal total = t != null && t.getTotal() != null ? t.getTotal() : BigDecimal.ZERO;
            filas[i + 1] = new String[] {
                    texto(c.getCodigoCotizacion()),
                    razonSocialDe(c.getCliente()),
                    c.getFechaCotizacion() != null ? c.getFechaCotizacion().format(FMT) : "",
                    c.getEstadoCotizacion() != null ? c.getEstadoCotizacion().getNombreEstado() : "",
                    PdfUtils.codigoMoneda(c.getMoneda()),
                    formatoMonto(total),
            };
        }
        return filas;
    }

    private String[][] filasOrdenesCompra(LocalDate desde, LocalDate hasta) {
        List<OrdenCompra> ordenes = ordenCompraRepository
                .findByStatusAndFechaRecepcionBetweenOrderByFechaRecepcionAsc("A", desde, hasta);

        String[][] filas = new String[ordenes.size() + 1][];
        filas[0] = new String[] { "Número", "Cotización origen", "Cliente", "Fecha recepción", "Estado" };
        for (int i = 0; i < ordenes.size(); i++) {
            OrdenCompra o = ordenes.get(i);
            Cotizacion cotizacion = o.getCotizacion();
            filas[i + 1] = new String[] {
                    texto(o.getNumeroOrdenCompra()),
                    cotizacion != null ? texto(cotizacion.getCodigoCotizacion()) : "",
                    cotizacion != null ? razonSocialDe(cotizacion.getCliente()) : "",
                    o.getFechaRecepcion() != null ? o.getFechaRecepcion().format(FMT) : "",
                    o.getEstadoOrdenCompra() != null ? o.getEstadoOrdenCompra().getNombreEstado() : "",
            };
        }
        return filas;
    }

    private String[][] filasFacturacion(LocalDate desde, LocalDate hasta) {
        List<Facturacion> facturas = facturacionRepository
                .findByStatusAndFechaEmisionBetweenOrderByFechaEmisionAsc("A", desde, hasta);

        String[][] filas = new String[facturas.size() + 1][];
        filas[0] = new String[] { "Comprobante", "Cliente", "Fecha emisión", "Fecha vencimiento", "Monto pagado", "Estado" };
        for (int i = 0; i < facturas.size(); i++) {
            Facturacion f = facturas.get(i);
            filas[i + 1] = new String[] {
                    texto(f.getSerieComprobante()) + "-" + texto(f.getNumeroComprobante()),
                    razonSocialDeFactura(f),
                    f.getFechaEmision() != null ? f.getFechaEmision().format(FMT) : "",
                    f.getFechaVencimiento() != null ? f.getFechaVencimiento().format(FMT) : "",
                    formatoMonto(f.getMontoPagado()),                    f.getEstadoFacturacion() != null ? f.getEstadoFacturacion().getNombreEstado() : "",            };
        }
        return filas;
    }

    /** Servicios y Productos no tienen una fecha de negocio propia para filtrar
     *  por rango (son catálogo, no movimiento); se listan completos (activos). */
    private String[][] filasServicios() {
        List<Servicio> servicios = servicioRepository.findByStatusOrderByNombreServicioAsc("A");

        String[][] filas = new String[servicios.size() + 1][];
        filas[0] = new String[] { "Código", "Nombre", "Categoría", "Unidad de medida", "Visible web" };
        for (int i = 0; i < servicios.size(); i++) {
            Servicio s = servicios.get(i);
            filas[i + 1] = new String[] {
                    texto(s.getCodigoServicio()),
                    texto(s.getNombreServicio()),
                    s.getCategoriaServicio() != null ? texto(s.getCategoriaServicio().getNombreCategoria()) : "",
                    s.getUnidadMedida() != null ? texto(s.getUnidadMedida().getNombreUnidad()) : "",
                    Boolean.TRUE.equals(s.getVisibleWeb()) ? "Sí" : "No",
            };
        }
        return filas;
    }

    private String[][] filasProductos() {
        List<Producto> productos = productoRepository.findByStatusOrderByNombreProductoAsc("A");

        String[][] filas = new String[productos.size() + 1][];
        filas[0] = new String[] { "Código", "Nombre", "Categoría", "Marca", "Visible web" };
        for (int i = 0; i < productos.size(); i++) {
            Producto p = productos.get(i);
            filas[i + 1] = new String[] {
                    texto(p.getCodigoProducto()),
                    texto(p.getNombreProducto()),
                    p.getCategoriaProducto() != null ? texto(p.getCategoriaProducto().getNombreCategoria()) : "",
                    // La marca es OPCIONAL en el catálogo (fabricación a medida):
                    // sin esta guarda el reporte reventaba con NullPointerException.
                    p.getMarca() != null ? texto(p.getMarca().getNombreMarca()) : "",
                    Boolean.TRUE.equals(p.getVisibleWeb()) ? "Sí" : "No",
            };
        }
        return filas;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    /** Razón social del cliente de una factura, tolerando eslabones nulos. */
    private String razonSocialDeFactura(Facturacion f) {
        if (f.getOrdenCompra() == null || f.getOrdenCompra().getCotizacion() == null) {
            return "";
        }
        return razonSocialDe(f.getOrdenCompra().getCotizacion().getCliente());
    }

    /** Razón social del cliente de una cotización (tercero), tolerando nulos. */
    private String razonSocialDe(Cliente cliente) {
        if (cliente == null || cliente.getTercero() == null) {
            return "";
        }
        return texto(cliente.getTercero().getRazonSocial());
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    /** Montos con 2 decimales, igual que en el archivo descargable. */
    private String formatoMonto(BigDecimal valor) {
        return PdfUtils.formatearMonto(valor);
    }

    // ------------------------------------------------------------------
    // Generación de archivos
    // ------------------------------------------------------------------

    private byte[] generarExcel(String[] encabezados, String[][] datos, String nombreHoja) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(nombreHoja);

            CellStyle estiloEncabezado = workbook.createCellStyle();
            Font fuenteEncabezado = workbook.createFont();
            fuenteEncabezado.setBold(true);
            estiloEncabezado.setFont(fuenteEncabezado);

            Row filaEncabezado = sheet.createRow(0);
            for (int col = 0; col < encabezados.length; col++) {
                Cell celda = filaEncabezado.createCell(col);
                celda.setCellValue(encabezados[col]);
                celda.setCellStyle(estiloEncabezado);
            }

            for (int i = 0; i < datos.length; i++) {
                Row fila = sheet.createRow(i + 1);
                for (int col = 0; col < datos[i].length; col++) {
                    fila.createCell(col).setCellValue(datos[i][col]);
                }
            }

            for (int col = 0; col < encabezados.length; col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel del reporte", e);
        }
    }
}