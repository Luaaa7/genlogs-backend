package com.genlogs.app.service.pdf;

import com.genlogs.app.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio centralizado para la generación de PDFs.
 */
@Service
public class PdfGenerationService {

    private static final float MARGEN_IZQ = 40f;
    private static final float ALTURA_LINEA = 16f;
    private static final float Y_INICIO = PDRectangle.A4.getHeight() - 50f;
    private static final float ANCHO_UTIL = PDRectangle.A4.getWidth() - 2 * MARGEN_IZQ;
    private static final float[] ANCHOS_COLUMNAS_4 = {
            ANCHO_UTIL * 0.52f,
            ANCHO_UTIL * 0.14f,
            ANCHO_UTIL * 0.17f,
            ANCHO_UTIL * 0.17f
    };
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

/** DTO para datos de cotización. */
    public record CotizacionPdfData(
            String codigo,
            String cliente,
            LocalDate fechaCotizacion,
            LocalDate fechaValidez,
            String simboloMoneda,
            List<String[]> lineas,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total
    ) {}

    /** DTO para datos de facturación. */
    public record FacturacionPdfData(
            String tipoComprobante,
            String numeroComprobante,
            String cliente,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String simboloMoneda,
            List<String[]> lineas,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total,
            BigDecimal pagado
    ) {}

    /** DTO para datos de reporte genérico. */
    public record ReportePdfData(
            String titulo,
            String[] encabezados,
            String[][] datos
    ) {}
    /** Genera PDF para una cotización. */
    public byte[] generarPdfCotizacion(CotizacionPdfData data) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);
            float y = Y_INICIO;

            // Título
            cs.setFont(fuenteTitulo, 14);
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText(PdfUtils.ajustarTexto("Cotización " + data.codigo(), fuenteTitulo, 14, ANCHO_UTIL));
            cs.endText();
            y -= ALTURA_LINEA * 1.4f;

            // Cliente
            cs.setFont(fuenteTexto, 10);
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText(PdfUtils.ajustarTexto("Cliente: " + data.cliente(), fuenteTexto, 10, ANCHO_UTIL));
            cs.endText();
            y -= ALTURA_LINEA;

            // Fechas
            String fechaCot = data.fechaCotizacion() != null ? data.fechaCotizacion().format(FMT_FECHA) : "-";
            String fechaVal = data.fechaValidez() != null ? data.fechaValidez().format(FMT_FECHA) : "-";
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText("Fecha: " + fechaCot + "   Válida hasta: " + fechaVal);
            cs.endText();
            y -= ALTURA_LINEA * 1.8f;

            // Encabezados tabla
            String[] encabezados = { "Descripción", "Cant.", "P. Unit.", "Importe" };
            y = PdfUtils.escribirFilaPdf(cs, fuenteTitulo, encabezados, MARGEN_IZQ, y, ANCHOS_COLUMNAS_4, ALTURA_LINEA);

            // Filas
            for (String[] fila : data.lineas()) {
                if (y < 100) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = Y_INICIO;
                }
                y = PdfUtils.escribirFilaPdf(cs, fuenteTexto, fila, MARGEN_IZQ, y, ANCHOS_COLUMNAS_4, ALTURA_LINEA);
            }

            y -= ALTURA_LINEA;

            // Totales
            cs.setFont(fuenteTexto, 10);
            for (String linea : List.of(
                    "Subtotal: " + data.simboloMoneda() + " " + data.subtotal().toPlainString(),
                    "IGV (18%): " + data.simboloMoneda() + " " + data.igv().toPlainString())) {
                if (y < 50) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = Y_INICIO;
                }
                cs.beginText();
                cs.newLineAtOffset(PDRectangle.A4.getWidth() - MARGEN_IZQ - 220, y);
                cs.showText(linea);
                cs.endText();
                y -= ALTURA_LINEA;
            }
            cs.setFont(fuenteTitulo, 11);
            cs.beginText();
            cs.newLineAtOffset(PDRectangle.A4.getWidth() - MARGEN_IZQ - 220, y);
            cs.showText("Total: " + data.simboloMoneda() + " " + data.total().toPlainString());
            cs.endText();

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la cotización", e);
        }
    }
/** Genera PDF para una facturación. */
    public byte[] generarPdfFacturacion(FacturacionPdfData data) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);
            float y = Y_INICIO;

            // Título (tipo + número)
            cs.setFont(fuenteTitulo, 14);
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText(PdfUtils.ajustarTexto(data.tipoComprobante() + " " + data.numeroComprobante(), fuenteTitulo, 14, ANCHO_UTIL));
            cs.endText();
            y -= ALTURA_LINEA * 1.4f;

            // Cliente
            cs.setFont(fuenteTexto, 10);
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText(PdfUtils.ajustarTexto("Cliente: " + data.cliente(), fuenteTexto, 10, ANCHO_UTIL));
            cs.endText();
            y -= ALTURA_LINEA;

            // Fechas
            String fechaEmi = data.fechaEmision() != null ? data.fechaEmision().format(FMT_FECHA) : "-";
            String fechaVen = data.fechaVencimiento() != null ? data.fechaVencimiento().format(FMT_FECHA) : "-";
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText("Emisión: " + fechaEmi + "   Vencimiento: " + fechaVen);
            cs.endText();
            y -= ALTURA_LINEA * 1.8f;

            // Encabezados tabla
            String[] encabezados = { "Descripción", "Cant.", "P. Unit.", "Importe" };
            y = PdfUtils.escribirFilaPdf(cs, fuenteTitulo, encabezados, MARGEN_IZQ, y, ANCHOS_COLUMNAS_4, ALTURA_LINEA);

            // Filas
            for (String[] fila : data.lineas()) {
                if (y < 100) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = Y_INICIO;
                }
                y = PdfUtils.escribirFilaPdf(cs, fuenteTexto, fila, MARGEN_IZQ, y, ANCHOS_COLUMNAS_4, ALTURA_LINEA);
            }

            y -= ALTURA_LINEA;

            // Totales y pagado
            cs.setFont(fuenteTexto, 10);
            for (String linea : List.of(
                    "Subtotal: " + data.simboloMoneda() + " " + data.subtotal().toPlainString(),
                    "IGV (18%): " + data.simboloMoneda() + " " + data.igv().toPlainString(),
                    "Pagado: " + data.simboloMoneda() + " " + data.pagado().toPlainString())) {
                if (y < 50) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = Y_INICIO;
                }
                cs.beginText();
                cs.newLineAtOffset(PDRectangle.A4.getWidth() - MARGEN_IZQ - 220, y);
                cs.showText(linea);
                cs.endText();
                y -= ALTURA_LINEA;
            }
            cs.setFont(fuenteTitulo, 11);
            cs.beginText();
            cs.newLineAtOffset(PDRectangle.A4.getWidth() - MARGEN_IZQ - 220, y);
            cs.showText("Total: " + data.simboloMoneda() + " " + data.total().toPlainString());
            cs.endText();

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la facturación", e);
        }
    }
/** Genera PDF genérico para reportes tabulares. */
    public byte[] generarPdfReporte(ReportePdfData data) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float anchoColumna = ANCHO_UTIL / data.encabezados().length;

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);
            float y = Y_INICIO;

            // Título del reporte
            cs.setFont(fuenteTitulo, 14);
            cs.beginText();
            cs.newLineAtOffset(MARGEN_IZQ, y);
            cs.showText("Reporte GENLOGS — " + data.titulo());
            cs.endText();
            y -= ALTURA_LINEA * 2;

            // Encabezados
            y = escribirFilaIgual(cs, fuenteTitulo, data.encabezados(), MARGEN_IZQ, y, anchoColumna, ALTURA_LINEA);

            // Filas
            for (String[] fila : data.datos()) {
                if (y < 50) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = Y_INICIO;
                }
                y = escribirFilaIgual(cs, fuenteTexto, fila, MARGEN_IZQ, y, anchoColumna, ALTURA_LINEA);
            }

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del reporte", e);
        }
    }

    /** Helper para filas con ancho de columna uniforme. */
    private float escribirFilaIgual(PDPageContentStream cs, PDType1Font fuente,
                                    String[] valores, float margenIzq, float y,
                                    float anchoColumna, float alturaLinea) throws IOException {
        cs.setFont(fuente, 9);
        float x = margenIzq;
        for (String valor : valores) {
            String texto = PdfUtils.ajustarTexto(valor, fuente, 9, anchoColumna - 4);
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(texto);
            cs.endText();
            x += anchoColumna;
        }
        return y - alturaLinea;
    }
}