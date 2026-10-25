package com.genlogs.app.service.pdf;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio centralizado para la generación de PDFs.
 * Cotización y factura comparten el mismo diseño: encabezado con logo y datos
 * del emisor, datos del cliente, tabla de ítems y totales con el mismo formato
 * numérico que la pantalla ("USD 4,209.30").
 */
@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);

    private static final float MARGEN_IZQ = 40f;
    private static final float ALTURA_LINEA = 16f;
    private static final float ANCHO_PAGINA = PDRectangle.A4.getWidth();
    private static final float Y_INICIO = PDRectangle.A4.getHeight() - 50f;
    private static final float ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN_IZQ;
    private static final float BORDE_DER = ANCHO_PAGINA - MARGEN_IZQ;
    private static final float[] ANCHOS_COLUMNAS_4 = {
            ANCHO_UTIL * 0.52f,
            ANCHO_UTIL * 0.12f,
            ANCHO_UTIL * 0.18f,
            ANCHO_UTIL * 0.18f
    };
    /** Ancho del logo en el PDF (puntos); el alto se calcula según la imagen. */
    private static final float LOGO_ANCHO = 170f;
    private static final Color GRIS_LINEA = new Color(200, 205, 215);
    private static final Color GRIS_TEXTO = new Color(90, 95, 105);
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Datos del emisor. Se configuran en application.properties / variables de entorno de Render. */
    @Value("${app.empresa.razon-social:GENLOGS S.A.C.}")
    private String empresaRazonSocial;

    @Value("${app.empresa.ruc:}")
    private String empresaRuc;

    @Value("${app.empresa.direccion:}")
    private String empresaDireccion;

    @Value("${app.empresa.contacto:}")
    private String empresaContacto;

    /** Logo con nombre en src/main/resources/pdf/logo.png. Si no existe, el nombre sale en texto. */
    private byte[] logoBytes;
    private boolean logoCargado;

    /** DTO para datos de cotización. Las líneas llegan ya formateadas (PdfUtils). */
    public record CotizacionPdfData(
            String codigo,
            String cliente,
            String clienteDocumento,
            String clienteDireccion,
            LocalDate fechaCotizacion,
            LocalDate fechaValidez,
            String condicionPago,
            String moneda,
            List<String[]> lineas,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total
    ) {}

    /** DTO para datos de facturación. Las líneas llegan ya formateadas (PdfUtils). */
    public record FacturacionPdfData(
            String tipoComprobante,
            String numeroComprobante,
            String cliente,
            String clienteDocumento,
            String clienteDireccion,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String condicionPago,
            String moneda,
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
        List<String[]> datosCliente = new ArrayList<>();
        datosCliente.add(new String[] { "Cliente", data.cliente() });
        agregarSiHay(datosCliente, "Documento", data.clienteDocumento());
        agregarSiHay(datosCliente, "Dirección", data.clienteDireccion());
        datosCliente.add(new String[] { "Fecha", fecha(data.fechaCotizacion()) + "      Válida hasta: " + fecha(data.fechaValidez()) });
        agregarSiHay(datosCliente, "Condición de pago", data.condicionPago());
        datosCliente.add(new String[] { "Moneda", data.moneda() });

        List<String[]> totales = List.of(
                new String[] { "Subtotal", monto(data.moneda(), data.subtotal()) },
                new String[] { "IGV (18%)", monto(data.moneda(), data.igv()) },
                new String[] { "Total", monto(data.moneda(), data.total()) });

        return generarDocumento("COTIZACIÓN", data.codigo(), datosCliente, data.lineas(), totales,
                "No se pudo generar el PDF de la cotización");
    }

    /** Genera PDF para una facturación. */
    public byte[] generarPdfFacturacion(FacturacionPdfData data) {
        List<String[]> datosCliente = new ArrayList<>();
        datosCliente.add(new String[] { "Cliente", data.cliente() });
        agregarSiHay(datosCliente, "Documento", data.clienteDocumento());
        agregarSiHay(datosCliente, "Dirección", data.clienteDireccion());
        datosCliente.add(new String[] { "Emisión", fecha(data.fechaEmision()) + "      Vencimiento: " + fecha(data.fechaVencimiento()) });
        agregarSiHay(datosCliente, "Forma de pago", data.condicionPago());
        datosCliente.add(new String[] { "Moneda", data.moneda() });

        BigDecimal pagado = data.pagado() != null ? data.pagado() : BigDecimal.ZERO;
        BigDecimal total = data.total() != null ? data.total() : BigDecimal.ZERO;
        List<String[]> totales = List.of(
                new String[] { "Subtotal", monto(data.moneda(), data.subtotal()) },
                new String[] { "IGV (18%)", monto(data.moneda(), data.igv()) },
                new String[] { "Pagado", monto(data.moneda(), pagado) },
                new String[] { "Saldo", monto(data.moneda(), total.subtract(pagado)) },
                new String[] { "Total", monto(data.moneda(), total) });

        String tipo = data.tipoComprobante() != null ? data.tipoComprobante().toUpperCase() : "COMPROBANTE";
        return generarDocumento(tipo, data.numeroComprobante(), datosCliente, data.lineas(), totales,
                "No se pudo generar el PDF de la facturación");
    }

    // ------------------------------------------------------------------
    // Diseño común de cotización y factura
    // ------------------------------------------------------------------

    private byte[] generarDocumento(String tipoDocumento, String numero, List<String[]> datosCliente,
                                    List<String[]> lineas, List<String[]> totales, String mensajeError) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);

            float y = dibujarEncabezado(documento, cs, negrita, normal, tipoDocumento, numero);

            // Datos del cliente y condiciones
            for (String[] dato : datosCliente) {
                texto(cs, negrita, 9, MARGEN_IZQ, y, dato[0] + ":");
                texto(cs, normal, 9, MARGEN_IZQ + 105, y,
                        PdfUtils.ajustarTexto(dato[1], normal, 9, ANCHO_UTIL - 105));
                y -= ALTURA_LINEA * 0.95f;
            }
            y -= ALTURA_LINEA * 0.8f;

            // Tabla
            y = dibujarEncabezadoTabla(cs, negrita, y);
            for (String[] fila : lineas) {
                if (y < 120) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = dibujarEncabezadoTabla(cs, negrita, Y_INICIO);
                }
                y = escribirFila(cs, normal, fila, y);
            }
            linea(cs, MARGEN_IZQ, y + ALTURA_LINEA * 0.6f, BORDE_DER);
            y -= ALTURA_LINEA * 0.6f;

            // Totales alineados a la derecha
            if (y < 40 + totales.size() * ALTURA_LINEA) {
                cs.close();
                pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                cs = new PDPageContentStream(documento, pagina);
                y = Y_INICIO;
            }
            float xEtiqueta = BORDE_DER - 220;
            for (int i = 0; i < totales.size(); i++) {
                boolean esTotal = i == totales.size() - 1;
                PDType1Font fuente = esTotal ? negrita : normal;
                float tam = esTotal ? 11 : 10;
                texto(cs, fuente, tam, xEtiqueta, y, totales.get(i)[0]);
                textoDerecha(cs, fuente, tam, BORDE_DER, y, totales.get(i)[1]);
                y -= ALTURA_LINEA;
            }

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(mensajeError, e);
        }
    }

    /** Logo con el nombre (GENLOGS S.A.C.) y datos del emisor a la izquierda; tipo y número a la derecha. */
    private float dibujarEncabezado(PDDocument documento, PDPageContentStream cs, PDType1Font negrita,
                                    PDType1Font normal, String tipoDocumento, String numero) throws IOException {
        float yTop = PDRectangle.A4.getHeight() - 40f;
        float y = yTop;

        boolean conLogo = false;
        byte[] logo = obtenerLogo();
        if (logo != null) {
            try {
                PDImageXObject imagen = PDImageXObject.createFromByteArray(documento, logo, "logo");
                float alto = LOGO_ANCHO * imagen.getHeight() / imagen.getWidth();
                cs.drawImage(imagen, MARGEN_IZQ, yTop - alto, LOGO_ANCHO, alto);
                y = yTop - alto - 12;
                conLogo = true;
            } catch (IOException | IllegalArgumentException e) {
                log.warn("No se pudo dibujar el logo en el PDF: {}", e.getMessage());
            }
        }

        // Sin logo, el nombre de la empresa va en texto.
        if (!conLogo) {
            y = yTop - 12;
            texto(cs, negrita, 12, MARGEN_IZQ, y, empresaRazonSocial);
            y -= 13;
        }
        if (empresaRuc != null && !empresaRuc.isBlank()) {
            texto(cs, normal, 9, MARGEN_IZQ, y, "RUC " + empresaRuc.trim());
            y -= 11;
        }
        if (empresaDireccion != null && !empresaDireccion.isBlank()) {
            texto(cs, normal, 9, MARGEN_IZQ, y, PdfUtils.ajustarTexto(empresaDireccion, normal, 9, 300));
            y -= 11;
        }
        if (empresaContacto != null && !empresaContacto.isBlank()) {
            texto(cs, normal, 9, MARGEN_IZQ, y, PdfUtils.ajustarTexto(empresaContacto, normal, 9, 300));
            y -= 11;
        }

        textoDerecha(cs, negrita, 13, BORDE_DER, yTop - 14, tipoDocumento);
        textoDerecha(cs, negrita, 11, BORDE_DER, yTop - 30, numero != null ? numero : "");

        float yLinea = Math.min(y + 4, yTop - 44) - 6;
        linea(cs, MARGEN_IZQ, yLinea, BORDE_DER);
        return yLinea - ALTURA_LINEA * 1.2f;
    }

    private float dibujarEncabezadoTabla(PDPageContentStream cs, PDType1Font negrita, float y) throws IOException {
        y = escribirFila(cs, negrita, new String[] { "Descripción", "Cant.", "P. Unit.", "Importe" }, y);
        linea(cs, MARGEN_IZQ, y + ALTURA_LINEA * 0.6f, BORDE_DER);
        return y - 4;
    }

    /** Descripción a la izquierda; cantidad, precio e importe alineados a la derecha. */
    private float escribirFila(PDPageContentStream cs, PDType1Font fuente, String[] valores, float y) throws IOException {
        float x = MARGEN_IZQ;
        for (int col = 0; col < valores.length && col < ANCHOS_COLUMNAS_4.length; col++) {
            float ancho = ANCHOS_COLUMNAS_4[col];
            String valor = PdfUtils.ajustarTexto(valores[col], fuente, 9, ancho - 6);
            if (col == 0) {
                texto(cs, fuente, 9, x, y, valor);
            } else {
                textoDerecha(cs, fuente, 9, x + ancho, y, valor);
            }
            x += ancho;
        }
        return y - ALTURA_LINEA;
    }

    // ------------------------------------------------------------------
    // Ayudas de dibujo
    // ------------------------------------------------------------------

    private void texto(PDPageContentStream cs, PDType1Font fuente, float tam, float x, float y, String valor)
            throws IOException {
        String limpio = PdfUtils.sanitizarTextoPdf(valor);
        if (limpio.isEmpty()) {
            return;
        }
        cs.setFont(fuente, tam);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(limpio);
        cs.endText();
    }

    private void textoDerecha(PDPageContentStream cs, PDType1Font fuente, float tam, float xDerecha, float y,
                              String valor) throws IOException {
        String limpio = PdfUtils.sanitizarTextoPdf(valor);
        float ancho = fuente.getStringWidth(limpio) / 1000f * tam;
        texto(cs, fuente, tam, xDerecha - ancho, y, limpio);
    }

    private void linea(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.setStrokingColor(GRIS_LINEA);
        cs.setLineWidth(0.7f);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
        cs.setStrokingColor(GRIS_TEXTO);
    }

    private static void agregarSiHay(List<String[]> lista, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) {
            lista.add(new String[] { etiqueta, valor });
        }
    }

    private static String fecha(LocalDate fecha) {
        return fecha != null ? fecha.format(FMT_FECHA) : "-";
    }

    private static String monto(String moneda, BigDecimal valor) {
        return moneda + " " + PdfUtils.formatearMonto(valor);
    }

    private synchronized byte[] obtenerLogo() {
        if (!logoCargado) {
            logoCargado = true;
            ClassPathResource recurso = new ClassPathResource("pdf/logo.png");
            if (recurso.exists()) {
                try (InputStream in = recurso.getInputStream()) {
                    logoBytes = in.readAllBytes();
                } catch (IOException e) {
                    log.warn("No se pudo leer pdf/logo.png: {}", e.getMessage());
                }
            }
        }
        return logoBytes;
    }

    // ------------------------------------------------------------------
    // Reportes (sin cambios)
    // ------------------------------------------------------------------

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