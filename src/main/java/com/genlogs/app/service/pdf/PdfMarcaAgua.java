package com.genlogs.app.service.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/**
 * Pone una marca de agua en diagonal en todas las páginas de un PDF.
 * Se usa en los comprobantes emitidos en modo simulado, para que nadie
 * los confunda con una factura real.
 */
public final class PdfMarcaAgua {

    private PdfMarcaAgua() {
    }

    public static byte[] aplicar(byte[] pdf, String titulo, String subtitulo) {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDExtendedGraphicsState transparencia = new PDExtendedGraphicsState();
            transparencia.setNonStrokingAlphaConstant(0.16f);

            for (PDPage pagina : doc.getPages()) {
                PDRectangle caja = pagina.getMediaBox();
                float cx = caja.getLowerLeftX() + caja.getWidth() / 2;
                float cy = caja.getLowerLeftY() + caja.getHeight() / 2;

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(transparencia);
                    cs.setNonStrokingColor(new Color(190, 30, 45));
                    escribirCentrado(cs, fuente, 64, titulo, cx, cy, 18);
                    escribirCentrado(cs, fuente, 26, subtitulo, cx, cy, -26);
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo poner la marca de agua al PDF", e);
        }
    }

    /** Nota pequeña y gris centrada al pie de cada página (discreta, no tapa el contenido). */
    public static byte[] notaPie(byte[] pdf, String texto) {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float tamano = 7.5f;
            float ancho = fuente.getStringWidth(texto) / 1000f * tamano;
            for (PDPage pagina : doc.getPages()) {
                PDRectangle caja = pagina.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(new Color(140, 148, 160));
                    cs.beginText();
                    cs.setFont(fuente, tamano);
                    cs.newLineAtOffset(caja.getLowerLeftX() + (caja.getWidth() - ancho) / 2, caja.getLowerLeftY() + 14);
                    cs.showText(texto);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo poner la nota al PDF", e);
        }
    }

    /** Texto girado 45° alrededor del centro de la página; dy lo sube o baja. */
    private static void escribirCentrado(PDPageContentStream cs, PDType1Font fuente, float tamano,
                                         String texto, float cx, float cy, float dy) throws IOException {
        if (texto == null || texto.isBlank()) {
            return;
        }
        float ancho = fuente.getStringWidth(texto) / 1000f * tamano;
        cs.beginText();
        cs.setFont(fuente, tamano);
        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), cx, cy));
        cs.newLineAtOffset(-ancho / 2, dy);
        cs.showText(texto);
        cs.endText();
    }
}