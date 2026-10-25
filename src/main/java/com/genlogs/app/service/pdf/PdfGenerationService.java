package com.genlogs.app.service.pdf;

import com.genlogs.app.config.EmisorProperties;
import com.genlogs.app.util.FormatoPdf;
import com.genlogs.app.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Servicio centralizado para la generación de PDFs.
 */
@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);
    private static final String RUTA_LOGO = "pdf/logo.png";
    private static final float ALTO_LOGO = 56f;
    private static final float ANCHO_CAJA_DOC = 190f;

    private final EmisorProperties emisor;

    public PdfGenerationService(EmisorProperties emisor) {
        this.emisor = emisor;
    }

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

/**
     * DTO para datos de cotización. Las líneas ya vienen formateadas:
     * {descripción, cantidad, precio unitario, importe}.
     */
    public record CotizacionPdfData(
            String codigo,
            String cliente,
            String clienteTipoDocumento,
            String clienteDocumento,
            String clienteDireccion,
            LocalDate fechaCotizacion,
            LocalDate fechaValidez,
            String condicionPago,
            String codigoMoneda,
            List<String[]> lineas,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total
    ) {}

    /**
     * DTO para datos de facturación. Las líneas ya vienen formateadas:
     * {descripción, cantidad, precio unitario, importe}.
     */
    public record FacturacionPdfData(
            String tipoComprobante,
            String numeroComprobante,
            String cliente,
            String clienteTipoDocumento,
            String clienteDocumento,
            String clienteDireccion,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String condicionPago,
            String codigoMoneda,
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
        List<String[]> datos = List.of(
                new String[] { "Fecha de cotización:", formatearFecha(data.fechaCotizacion()) },
                new String[] { "Válida hasta:", formatearFecha(data.fechaValidez()) },
                new String[] { "Forma de pago:", valorODash(data.condicionPago()) },
                new String[] { "Moneda:", valorODash(data.codigoMoneda()) });
        return generarDocumentoComercial(new DocumentoComercial(
                "Cotización", data.codigo(), data.cliente(), data.clienteTipoDocumento(),
                data.clienteDocumento(), data.clienteDireccion(), datos, data.codigoMoneda(),
                data.lineas(), data.subtotal(), data.igv(), data.total(), null),
                "No se pudo generar el PDF de la cotización");
    }

    /** Genera PDF para una facturación. */
    public byte[] generarPdfFacturacion(FacturacionPdfData data) {
        List<String[]> datos = List.of(
                new String[] { "Fecha de emisión:", formatearFecha(data.fechaEmision()) },
                new String[] { "Fecha de vencimiento:", formatearFecha(data.fechaVencimiento()) },
                new String[] { "Forma de pago:", valorODash(data.condicionPago()) },
                new String[] { "Moneda:", valorODash(data.codigoMoneda()) });
        return generarDocumentoComercial(new DocumentoComercial(
                data.tipoComprobante(), data.numeroComprobante(), data.cliente(), data.clienteTipoDocumento(),
                data.clienteDocumento(), data.clienteDireccion(), datos, data.codigoMoneda(),
                data.lineas(), data.subtotal(), data.igv(), data.total(),
                data.pagado() != null ? data.pagado() : BigDecimal.ZERO),
                "No se pudo generar el PDF de la facturación");
    }

    /** Datos comunes de un documento comercial (cotización o comprobante). {@code pagado} null = no se muestra. */
    private record DocumentoComercial(
            String tipoDocumento, String numeroDocumento, String cliente,
            String clienteTipoDocumento, String clienteDocumento, String clienteDireccion,
            List<String[]> datosDocumento, String codigoMoneda, List<String[]> lineas,
            BigDecimal subtotal, BigDecimal igv, BigDecimal total, BigDecimal pagado) {}

    /** Dibuja cabecera, cliente, datos del documento, tabla de ítems y totales. */
    private byte[] generarDocumentoComercial(DocumentoComercial d, String mensajeError) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);
            float y = Y_INICIO;

            // Cabecera: logo + datos del emisor + recuadro con RUC, tipo y número
            y = dibujarCabecera(documento, cs, fuenteTitulo, fuenteTexto,
                    d.tipoDocumento(), d.numeroDocumento(), y);

            // Datos del cliente
            y = escribirLinea(cs, fuenteTitulo, fuenteTexto, "Cliente:", d.cliente(), y);
            String etiquetaDoc = (d.clienteTipoDocumento() != null && !d.clienteTipoDocumento().isBlank())
                    ? d.clienteTipoDocumento() : "Documento";
            y = escribirLinea(cs, fuenteTitulo, fuenteTexto, etiquetaDoc + ":", valorODash(d.clienteDocumento()), y);
            y = escribirLinea(cs, fuenteTitulo, fuenteTexto, "Dirección:", valorODash(d.clienteDireccion()), y);

            // Fechas, forma de pago y moneda
            for (String[] dato : d.datosDocumento()) {
                y = escribirLinea(cs, fuenteTitulo, fuenteTexto, dato[0], dato[1], y);
            }
            y -= ALTURA_LINEA * 0.8f;

            // Encabezados de la tabla
            y = dibujarEncabezadoTabla(cs, fuenteTitulo, y);

            // Filas (la descripción puede ocupar varias líneas)
            for (String[] fila : d.lineas()) {
                float altoFila = PdfUtils.alturaFilaTabla(fila, fuenteTexto, ANCHOS_COLUMNAS_4[0], ALTURA_LINEA);
                if (y - altoFila < 150) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = dibujarEncabezadoTabla(cs, fuenteTitulo, Y_INICIO);
                }
                y = PdfUtils.escribirFilaTabla(cs, fuenteTexto, fila, MARGEN_IZQ, y, ANCHOS_COLUMNAS_4, ALTURA_LINEA);
            }

            y -= ALTURA_LINEA * 0.5f;

            // Totales (si no caben, pasan a una página nueva)
            if (y < 130) {
                cs.close();
                pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                cs = new PDPageContentStream(documento, pagina);
                y = Y_INICIO;
            }
            String moneda = d.codigoMoneda();
            y = escribirTotal(cs, fuenteTexto, "Subtotal", FormatoPdf.monto(moneda, d.subtotal()), y);
            y = escribirTotal(cs, fuenteTexto, "IGV (18%)", FormatoPdf.monto(moneda, d.igv()), y);
            y = escribirTotal(cs, fuenteTitulo, "Total", FormatoPdf.monto(moneda, d.total()), y);
            if (d.pagado() != null) {
                BigDecimal saldo = d.total().subtract(d.pagado()).max(BigDecimal.ZERO);
                y = escribirTotal(cs, fuenteTexto, "Pagado", FormatoPdf.monto(moneda, d.pagado()), y);
                escribirTotal(cs, fuenteTitulo, "Saldo pendiente", FormatoPdf.monto(moneda, saldo), y);
            }

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(mensajeError, e);
        }
    }


    // ------------------------------------------------------------------
    // Piezas compartidas por los PDF de cotización y factura
    // ------------------------------------------------------------------

    /**
     * Dibuja la cabecera: logo y datos del emisor a la izquierda, y a la derecha un recuadro
     * con el RUC del emisor, el tipo de documento y su número.
     *
     * @return Coordenada Y donde puede seguir el contenido
     */
    private float dibujarCabecera(PDDocument documento, PDPageContentStream cs,
                                  PDType1Font fuenteTitulo, PDType1Font fuenteTexto,
                                  String tipoDocumento, String numeroDocumento, float y) throws IOException {
        float xTexto = MARGEN_IZQ;
        PDImageXObject logo = cargarLogo(documento);
        if (logo != null) {
            float ancho = ALTO_LOGO * logo.getWidth() / logo.getHeight();
            cs.drawImage(logo, MARGEN_IZQ, y - ALTO_LOGO, ancho, ALTO_LOGO);
            xTexto = MARGEN_IZQ + ancho + 12f;
        }

        // Emisor
        float anchoMaxEmisor = PDRectangle.A4.getWidth() - MARGEN_IZQ - ANCHO_CAJA_DOC - 12f - xTexto;
        float yTexto = y - 12f;
        cs.setFont(fuenteTitulo, 12);
        cs.beginText();
        cs.newLineAtOffset(xTexto, yTexto);
        cs.showText(PdfUtils.ajustarTexto(emisor.getRazonSocial(), fuenteTitulo, 12, anchoMaxEmisor));
        cs.endText();
        yTexto -= 15f;
        cs.setFont(fuenteTexto, 9);
        cs.beginText();
        cs.newLineAtOffset(xTexto, yTexto);
        cs.showText(PdfUtils.ajustarTexto("RUC: " + emisor.getRuc(), fuenteTexto, 9, anchoMaxEmisor));
        cs.endText();
        if (emisor.getDireccion() != null && !emisor.getDireccion().isBlank()) {
            yTexto -= 13f;
            cs.beginText();
            cs.newLineAtOffset(xTexto, yTexto);
            cs.showText(PdfUtils.ajustarTexto(emisor.getDireccion(), fuenteTexto, 9, anchoMaxEmisor));
            cs.endText();
        }

        // Recuadro del documento
        float xCaja = PDRectangle.A4.getWidth() - MARGEN_IZQ - ANCHO_CAJA_DOC;
        float yCaja = y - ALTO_LOGO;
        cs.setLineWidth(0.8f);
        cs.setStrokingColor(0.3f);
        cs.addRect(xCaja, yCaja, ANCHO_CAJA_DOC, ALTO_LOGO);
        cs.stroke();
        escribirCentrado(cs, fuenteTitulo, 10, "RUC: " + emisor.getRuc(), xCaja, ANCHO_CAJA_DOC, y - 16f);
        escribirCentrado(cs, fuenteTitulo, 11, tipoDocumento == null ? "" : tipoDocumento.toUpperCase(Locale.ROOT), xCaja, ANCHO_CAJA_DOC, y - 31f);
        escribirCentrado(cs, fuenteTitulo, 11, numeroDocumento, xCaja, ANCHO_CAJA_DOC, y - 46f);

        return y - ALTO_LOGO - 26f;
    }

    /** Carga el logo desde el classpath; si no existe o falla, el PDF se genera sin logo. */
    private PDImageXObject cargarLogo(PDDocument documento) {
        try (InputStream in = new ClassPathResource(RUTA_LOGO).getInputStream()) {
            return PDImageXObject.createFromByteArray(documento, in.readAllBytes(), "logo");
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo cargar el logo del PDF ({}): {}", RUTA_LOGO, e.getMessage());
            return null;
        }
    }

    private void escribirCentrado(PDPageContentStream cs, PDType1Font fuente, float tamano,
                                  String texto, float x, float ancho, float y) throws IOException {
        String limpio = PdfUtils.ajustarTexto(texto, fuente, tamano, ancho - 8f);
        float xTexto = x + (ancho - PdfUtils.anchoTexto(limpio, fuente, tamano)) / 2f;
        cs.setFont(fuente, tamano);
        cs.beginText();
        cs.newLineAtOffset(xTexto, y);
        cs.showText(limpio);
        cs.endText();
    }

    /** Escribe "Etiqueta: valor" con la etiqueta en negrita. Devuelve la nueva Y. */
    private float escribirLinea(PDPageContentStream cs, PDType1Font fuenteEtiqueta, PDType1Font fuenteValor,
                                String etiqueta, String valor, float y) throws IOException {
        cs.setFont(fuenteEtiqueta, 10);
        cs.beginText();
        cs.newLineAtOffset(MARGEN_IZQ, y);
        cs.showText(PdfUtils.sanitizarTextoPdf(etiqueta));
        cs.endText();
        float xValor = MARGEN_IZQ + PdfUtils.anchoTexto(PdfUtils.sanitizarTextoPdf(etiqueta), fuenteEtiqueta, 10) + 6f;
        cs.setFont(fuenteValor, 10);
        cs.beginText();
        cs.newLineAtOffset(xValor, y);
        cs.showText(PdfUtils.ajustarTexto(valor, fuenteValor, 10, ANCHO_UTIL - (xValor - MARGEN_IZQ)));
        cs.endText();
        return y - ALTURA_LINEA;
    }

    /** Encabezados de la tabla de ítems con una línea debajo. Devuelve la nueva Y. */
    private float dibujarEncabezadoTabla(PDPageContentStream cs, PDType1Font fuenteTitulo, float y) throws IOException {
        cs.setFont(fuenteTitulo, 9);
        float x = MARGEN_IZQ;
        String[] encabezados = { "Descripción", "Cant.", "P. Unit.", "Importe" };
        for (int col = 0; col < encabezados.length; col++) {
            float anchoCol = ANCHOS_COLUMNAS_4[col];
            float xTexto = col == 0 ? x : x + anchoCol - 4 - PdfUtils.anchoTexto(encabezados[col], fuenteTitulo, 9);
            cs.beginText();
            cs.newLineAtOffset(xTexto, y);
            cs.showText(encabezados[col]);
            cs.endText();
            x += anchoCol;
        }
        cs.setLineWidth(0.6f);
        cs.setStrokingColor(0.3f);
        cs.moveTo(MARGEN_IZQ, y - 4f);
        cs.lineTo(MARGEN_IZQ + ANCHO_UTIL, y - 4f);
        cs.stroke();
        return y - ALTURA_LINEA - 2f;
    }

    /** Fila de totales: etiqueta a la izquierda del bloque y valor alineado a la derecha del margen. */
    private float escribirTotal(PDPageContentStream cs, PDType1Font fuente, String etiqueta,
                                String valor, float y) throws IOException {
        float xDerecha = PDRectangle.A4.getWidth() - MARGEN_IZQ;
        cs.setFont(fuente, 10);
        cs.beginText();
        cs.newLineAtOffset(xDerecha - 220f, y);
        cs.showText(PdfUtils.sanitizarTextoPdf(etiqueta));
        cs.endText();
        cs.beginText();
        cs.newLineAtOffset(xDerecha - PdfUtils.anchoTexto(valor, fuente, 10), y);
        cs.showText(valor);
        cs.endText();
        return y - ALTURA_LINEA;
    }

    private static String formatearFecha(LocalDate fecha) {
        return fecha != null ? fecha.format(FMT_FECHA) : "-";
    }

    private static String valorODash(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
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