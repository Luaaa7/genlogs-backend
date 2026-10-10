package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ServicioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Backend del módulo de Reportes de Luana. Antes de esto, el frontend
 * (ReportesPage.tsx -> useGenerarReporte -> reportesApi.generarReporte)
 * llamaba a POST /api/reportes/generar y el backend no tenía NADA bajo ese
 * path: 404 garantizado. Esta clase arma el archivo (Excel con Apache POI o
 * PDF con PDFBox) para los 5 tipos de reporte que el frontend ya sabe pedir.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CotizacionRepository cotizacionRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturacionRepository facturacionRepository;
    private final ServicioRepository servicioRepository;
    private final ProductoRepository productoRepository;

    public record ArchivoGenerado(byte[] contenido, String nombreArchivo, String contentType) {}

    @Transactional(readOnly = true)
    public ArchivoGenerado generar(ReporteRequest request) {
        if (request.getFechaInicio().isAfter(request.getFechaFin())) {
            throw new BusinessException("La fecha de inicio no puede ser mayor a la fecha fin");
        }

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
            byte[] bytes = generarPdf(encabezados, datos, request.getTipoReporte().name());
            return new ArchivoGenerado(bytes, nombreBase + ".pdf", "application/pdf");
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
        List<Cotizacion> cotizaciones = cotizacionRepository.findAll().stream()
                .filter(c -> "A".equals(c.getStatus()))
                .filter(c -> !c.getFechaCotizacion().isBefore(desde) && !c.getFechaCotizacion().isAfter(hasta))
                .sorted((a, b) -> a.getFechaCotizacion().compareTo(b.getFechaCotizacion()))
                .toList();

        String[][] filas = new String[cotizaciones.size() + 1][];
        filas[0] = new String[] { "Código", "Cliente", "Fecha", "Estado", "Moneda", "Total" };
        for (int i = 0; i < cotizaciones.size(); i++) {
            Cotizacion c = cotizaciones.get(i);
            var totales = cotizacionRepository.findTotalesByCotizacion(c.getIdCotizacion()).orElse(null);
            filas[i + 1] = new String[] {
                    c.getCodigoCotizacion(),
                    c.getCliente().getTercero().getRazonSocial(),
                    c.getFechaCotizacion().format(FMT),
                    c.getEstadoCotizacion().getNombreEstado(),
                    c.getMoneda().getSimbolo(),
                    totales != null ? totales.getTotal().toPlainString() : "0.00",
            };
        }
        return filas;
    }

    private String[][] filasOrdenesCompra(LocalDate desde, LocalDate hasta) {
        List<OrdenCompra> ordenes = ordenCompraRepository.findAll().stream()
                .filter(o -> "A".equals(o.getStatus()))
                .filter(o -> !o.getFechaRecepcion().isBefore(desde) && !o.getFechaRecepcion().isAfter(hasta))
                .sorted((a, b) -> a.getFechaRecepcion().compareTo(b.getFechaRecepcion()))
                .toList();

        String[][] filas = new String[ordenes.size() + 1][];
        filas[0] = new String[] { "Número", "Cotización origen", "Cliente", "Fecha recepción", "Estado" };
        for (int i = 0; i < ordenes.size(); i++) {
            OrdenCompra o = ordenes.get(i);
            filas[i + 1] = new String[] {
                    o.getNumeroOrdenCompra(),
                    o.getCotizacion().getCodigoCotizacion(),
                    o.getCotizacion().getCliente().getTercero().getRazonSocial(),
                    o.getFechaRecepcion().format(FMT),
                    o.getEstadoOrdenCompra().getNombreEstado(),
            };
        }
        return filas;
    }

    private String[][] filasFacturacion(LocalDate desde, LocalDate hasta) {
        List<Facturacion> facturas = facturacionRepository.findAll().stream()
                .filter(f -> "A".equals(f.getStatus()))
                .filter(f -> !f.getFechaEmision().isBefore(desde) && !f.getFechaEmision().isAfter(hasta))
                .sorted((a, b) -> a.getFechaEmision().compareTo(b.getFechaEmision()))
                .toList();

        String[][] filas = new String[facturas.size() + 1][];
        filas[0] = new String[] { "Comprobante", "Cliente", "Fecha emisión", "Fecha vencimiento", "Monto pagado", "Estado" };
        for (int i = 0; i < facturas.size(); i++) {
            Facturacion f = facturas.get(i);
            filas[i + 1] = new String[] {
                    f.getSerieComprobante() + "-" + f.getNumeroComprobante(),
                    f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial(),
                    f.getFechaEmision().format(FMT),
                    f.getFechaVencimiento().format(FMT),
                    f.getMontoPagado().toPlainString(),
                    f.getEstadoFacturacion().getNombreEstado(),
            };
        }
        return filas;
    }

    /** Servicios y Productos no tienen una fecha de negocio propia para filtrar
     *  por rango (son catálogo, no movimiento); se listan completos (activos). */
    private String[][] filasServicios() {
        List<Servicio> servicios = servicioRepository.findAll().stream()
                .filter(s -> "A".equals(s.getStatus()))
                .sorted((a, b) -> a.getNombreServicio().compareTo(b.getNombreServicio()))
                .toList();

        String[][] filas = new String[servicios.size() + 1][];
        filas[0] = new String[] { "Código", "Nombre", "Categoría", "Unidad de medida", "Visible web" };
        for (int i = 0; i < servicios.size(); i++) {
            Servicio s = servicios.get(i);
            filas[i + 1] = new String[] {
                    s.getCodigoServicio(),
                    s.getNombreServicio(),
                    s.getCategoriaServicio().getNombreCategoria(),
                    s.getUnidadMedida().getNombreUnidad(),
                    Boolean.TRUE.equals(s.getVisibleWeb()) ? "Sí" : "No",
            };
        }
        return filas;
    }

    private String[][] filasProductos() {
        List<Producto> productos = productoRepository.findAll().stream()
                .filter(p -> "A".equals(p.getStatus()))
                .sorted((a, b) -> a.getNombreProducto().compareTo(b.getNombreProducto()))
                .toList();

        String[][] filas = new String[productos.size() + 1][];
        filas[0] = new String[] { "Código", "Nombre", "Categoría", "Marca", "Visible web" };
        for (int i = 0; i < productos.size(); i++) {
            Producto p = productos.get(i);
            filas[i + 1] = new String[] {
                    p.getCodigoProducto(),
                    p.getNombreProducto(),
                    p.getCategoriaProducto().getNombreCategoria(),
                    p.getMarca().getNombreMarca(),
                    Boolean.TRUE.equals(p.getVisibleWeb()) ? "Sí" : "No",
            };
        }
        return filas;
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

    private byte[] generarPdf(String[] encabezados, String[][] datos, String titulo) {
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

            cs.setFont(fuenteTitulo, 14);
            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText("Reporte GENLOGS — " + titulo);
            cs.endText();
            y -= alturaLinea * 2;

            y = escribirFila(cs, fuenteTitulo, encabezados, margenIzq, y, anchoColumna, alturaLinea);

            for (String[] fila : datos) {
                if (y < 50) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = yInicio;
                }
                y = escribirFila(cs, fuenteTexto, fila, margenIzq, y, anchoColumna, alturaLinea);
            }
            cs.close();

            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo PDF del reporte", e);
        }
    }

    private float escribirFila(PDPageContentStream cs, PDType1Font fuente, String[] valores,
            float margenIzq, float y, float anchoColumna, float alturaLinea) throws IOException {
        cs.setFont(fuente, 9);
        float x = margenIzq;
        for (String valor : valores) {
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(valor == null ? "" : valor.length() > 28 ? valor.substring(0, 25) + "..." : valor);
            cs.endText();
            x += anchoColumna;
        }
        return y - alturaLinea;
    }
}
