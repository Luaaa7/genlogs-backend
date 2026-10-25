package com.genlogs.app.util;

import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Tercero;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utilidades compartidas para generación de PDFs con PDFBox.
 * Centraliza la sanitización, ajuste de texto y escritura de filas/encabezados.
 */
public final class PdfUtils {

    private PdfUtils() {
        // Utility class
    }

    // ------------------------------------------------------------------
    // Formato de números (igual que la pantalla: 1,234.50)
    // ------------------------------------------------------------------

    private static DecimalFormat formato(String patron) {
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Locale.US));
    }

    /** Importe con 2 decimales y separador de miles: 1560 -> "1,560.00". */
    public static String formatearMonto(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        return formato("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP));
    }

    /** Cantidad sin ceros de relleno: 2.000 -> "2", 2.500 -> "2.5". */
    public static String formatearCantidad(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        return formato("#,##0.###").format(v.stripTrailingZeros());
    }

    /** Código ISO de la moneda ("USD", "PEN"), como en la pantalla. Nunca "$". */
    public static String codigoMoneda(Moneda moneda) {
        if (moneda == null) {
            return "PEN";
        }
        if (moneda.getCodigoMoneda() != null && !moneda.getCodigoMoneda().isBlank()) {
            return moneda.getCodigoMoneda().trim();
        }
        return moneda.getSimbolo() != null ? moneda.getSimbolo() : "PEN";
    }

    /** "RUC 20114915026" (o el tipo que corresponda); vacío si no hay documento. */
    public static String documentoTercero(Tercero tercero) {
        if (tercero == null || tercero.getNumeroDocumento() == null || tercero.getNumeroDocumento().isBlank()) {
            return "";
        }
        String tipo = tercero.getTipoDocumento() != null && tercero.getTipoDocumento().getCodigoTipo() != null
                ? tercero.getTipoDocumento().getCodigoTipo().trim() + " "
                : "";
        return tipo + tercero.getNumeroDocumento().trim();
    }

    /** Dirección del tercero; vacío si no tiene. */
    public static String direccionTercero(Tercero tercero) {
        return tercero != null && tercero.getDireccion() != null ? tercero.getDireccion().trim() : "";
    }

    /**
     * Sanea caracteres y saltos de línea para PDFBox.
     *
     * @param texto Texto original (puede ser null)
     * @return Texto seguro para PDFBox
     */
    public static String sanitizarTextoPdf(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replace("\u2014", "-")
                .replace("\u2013", "-")
                .replace("\u2022", "*")
                .replace("\u201c", "\"")
                .replace("\u201d", "\"")
                .replace("\u2018", "'")
                .replace("\u2019", "'")
                .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", " ")
                .trim();
    }

    /**
     * Trunca texto largo con elipsis evitando desbordes.
     *
     * @param texto       Texto original
     * @param fuente      Fuente PDFBox
     * @param fontSize    Tamaño de fuente
     * @param anchoMaximo Ancho máximo permitido
     * @return Texto ajustado
     */
    public static String ajustarTexto(String texto, PDType1Font fuente, float fontSize, float anchoMaximo) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String limpio = sanitizarTextoPdf(texto);
        try {
            float ancho = (fuente.getStringWidth(limpio) / 1000f) * fontSize;
            if (ancho <= anchoMaximo) {
                return limpio;
            }
            while (limpio.length() > 3 &&
                    (fuente.getStringWidth(limpio + "...") / 1000f) * fontSize > anchoMaximo) {
                limpio = limpio.substring(0, limpio.length() - 1);
            }
            return limpio + "...";
        } catch (Exception e) {
            // fallback simple si falla medición de fuente
            return limpio.length() > 35 ? limpio.substring(0, 32) + "..." : limpio;
        }
    }

    /**
     * Escribe una fila de celdas en el PDF.
     *
     * @param cs             Content stream actual
     * @param fuente         Fuente a usar
     * @param valores        Array de textos por columna
     * @param margenIzq      Margen izquierdo
     * @param y              Coordenada Y inicial
     * @param anchosColumnas Anchos de cada columna
     * @param alturaLinea    Altura de línea
     * @return Nueva coordenada Y después de la fila
     * @throws IOException Si hay error escribiendo en el stream
     */
    public static float escribirFilaPdf(PDPageContentStream cs, PDType1Font fuente,
                                         String[] valores, float margenIzq, float y,
                                         float[] anchosColumnas, float alturaLinea) throws IOException {
        cs.setFont(fuente, 9);
        float x = margenIzq;
        for (int col = 0; col < valores.length; col++) {
            float anchoCol = anchosColumnas[col];
            String texto = ajustarTexto(valores[col], fuente, 9, anchoCol - 4);
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(texto);
            cs.endText();
            x += anchoCol;
        }
        return y - alturaLinea;
    }

    /**
     * Escribe una fila de encabezado (negrita) en el PDF.
     *
     * @param cs             Content stream actual
     * @param fuenteTitulo   Fuente en negrita
     * @param valores        Textos de encabezado
     * @param margenIzq      Margen izquierdo
     * @param y              Coordenada Y inicial
     * @param anchosColumnas Anchos de cada columna
     * @param alturaLinea    Altura de línea
     * @return Nueva coordenada Y después de la fila
     * @throws IOException Si hay error escribiendo en el stream
     */
    public static float escribirEncabezadoPdf(PDPageContentStream cs, PDType1Font fuenteTitulo,
                                               String[] valores, float margenIzq, float y,
                                               float[] anchosColumnas, float alturaLinea) throws IOException {
        return escribirFilaPdf(cs, fuenteTitulo, valores, margenIzq, y, anchosColumnas, alturaLinea);
    }
}