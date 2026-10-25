package com.genlogs.app.util;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades compartidas para generación de PDFs con PDFBox.
 * Centraliza la sanitización, ajuste de texto y escritura de filas/encabezados.
 */
public final class PdfUtils {

    private PdfUtils() {
        // Utility class
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

    /** Máximo de líneas que ocupa una descripción en una celda; si no cabe se corta con "...". */
    public static final int MAX_LINEAS_CELDA = 4;

    /**
     * Parte un texto en varias líneas que caben en el ancho indicado (por palabras).
     * Una palabra más larga que el ancho se corta a la fuerza. Devuelve siempre al menos una línea.
     */
    public static List<String> dividirEnLineas(String texto, PDType1Font fuente, float fontSize, float anchoMaximo) {
        List<String> lineas = new ArrayList<>();
        String limpio = sanitizarTextoPdf(texto);
        if (limpio.isEmpty()) {
            lineas.add("");
            return lineas;
        }
        try {
            StringBuilder actual = new StringBuilder();
            for (String palabra : limpio.split(" +")) {
                String candidata = actual.length() == 0 ? palabra : actual + " " + palabra;
                if (anchoTexto(candidata, fuente, fontSize) <= anchoMaximo) {
                    actual.setLength(0);
                    actual.append(candidata);
                    continue;
                }
                if (actual.length() > 0) {
                    lineas.add(actual.toString());
                    actual.setLength(0);
                }
                // La palabra sola puede no caber: se corta caracter a caracter.
                String resto = palabra;
                while (anchoTexto(resto, fuente, fontSize) > anchoMaximo && resto.length() > 1) {
                    int corte = resto.length() - 1;
                    while (corte > 1 && anchoTexto(resto.substring(0, corte), fuente, fontSize) > anchoMaximo) {
                        corte--;
                    }
                    lineas.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                actual.append(resto);
            }
            if (actual.length() > 0) {
                lineas.add(actual.toString());
            }
        } catch (IOException e) {
            lineas.clear();
            lineas.add(ajustarTexto(limpio, fuente, fontSize, anchoMaximo));
            return lineas;
        }
        if (lineas.size() > MAX_LINEAS_CELDA) {
            List<String> recortadas = new ArrayList<>(lineas.subList(0, MAX_LINEAS_CELDA));
            String ultima = recortadas.get(MAX_LINEAS_CELDA - 1);
            recortadas.set(MAX_LINEAS_CELDA - 1, ajustarTexto(ultima + "...", fuente, fontSize, anchoMaximo));
            return recortadas;
        }
        return lineas;
    }

    /** Ancho en puntos de un texto con la fuente y tamaño dados. */
    public static float anchoTexto(String texto, PDType1Font fuente, float fontSize) throws IOException {
        return (fuente.getStringWidth(texto) / 1000f) * fontSize;
    }

    /** Altura que ocupará una fila de tabla cuya primera columna (descripción) puede ocupar varias líneas. */
    public static float alturaFilaTabla(String[] valores, PDType1Font fuente, float anchoPrimeraColumna, float alturaLinea) {
        int lineas = dividirEnLineas(valores[0], fuente, 9, anchoPrimeraColumna - 4).size();
        return Math.max(1, lineas) * alturaLinea;
    }

    /**
     * Escribe una fila de tabla: la primera columna (descripción) se parte en varias líneas
     * y las demás (cantidad, precio, importe) van alineadas a la derecha.
     *
     * @return Nueva coordenada Y debajo de la fila
     */
    public static float escribirFilaTabla(PDPageContentStream cs, PDType1Font fuente,
                                          String[] valores, float margenIzq, float y,
                                          float[] anchosColumnas, float alturaLinea) throws IOException {
        cs.setFont(fuente, 9);
        float x = margenIzq;
        int lineasDescripcion = 1;
        for (int col = 0; col < valores.length; col++) {
            float anchoCol = anchosColumnas[col];
            if (col == 0) {
                List<String> lineas = dividirEnLineas(valores[0], fuente, 9, anchoCol - 4);
                lineasDescripcion = Math.max(1, lineas.size());
                float yLinea = y;
                for (String linea : lineas) {
                    cs.beginText();
                    cs.newLineAtOffset(x, yLinea);
                    cs.showText(linea);
                    cs.endText();
                    yLinea -= alturaLinea;
                }
            } else {
                String texto = ajustarTexto(valores[col], fuente, 9, anchoCol - 4);
                cs.beginText();
                cs.newLineAtOffset(x + anchoCol - 4 - anchoTexto(texto, fuente, 9), y);
                cs.showText(texto);
                cs.endText();
            }
            x += anchoCol;
        }
        return y - lineasDescripcion * alturaLinea;
    }
}
