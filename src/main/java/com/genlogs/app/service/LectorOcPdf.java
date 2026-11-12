package com.genlogs.app.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lee el N.º, la fecha, el monto y la moneda de un PDF de orden de compra.
 * No usa JPA ni la web. Si el archivo no se puede leer, devuelve todo nulo.
 */
@Component
public class LectorOcPdf {

    private static final Logger log = LoggerFactory.getLogger(LectorOcPdf.class);
    private static final int PAGINAS = 5;

    private static final Pattern NUMERO = Pattern.compile(
            "(?i)(?:orden\\s+de\\s+compra|\\bpedido\\b|\\boc\\b|\\bnro\\.?|n\\s*[°º])[^\\d]{0,20}(\\d{8,12})");
    private static final Pattern TOTAL = Pattern.compile(
            "(?i)(?:importe\\s+total|valor\\s+total|\\btotal\\b)(.{0,50})");
    private static final Pattern MONTO = Pattern.compile(
            "(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2})?|\\d+[.,]\\d{2})");
    private static final Pattern FECHA_NUM = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern FECHA_LARGA = Pattern.compile(
            "(?i)(\\d{1,2})\\s+de\\s+([a-záéíóúü]+)\\s+de\\s+(\\d{4})");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3),
            Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6),
            Map.entry("julio", 7), Map.entry("agosto", 8), Map.entry("septiembre", 9),
            Map.entry("setiembre", 9), Map.entry("octubre", 10), Map.entry("noviembre", 11),
            Map.entry("diciembre", 12));

    public LecturaOc leer(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            log.warn("No se leyó la OC: el PDF está vacío");
            return LecturaOc.vacia();
        }
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int paginas = documento.getNumberOfPages();
            if (paginas == 0) {
                log.warn("No se leyó la OC: el PDF no tiene páginas");
                return LecturaOc.vacia();
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(PAGINAS, paginas));
            String texto = stripper.getText(documento);
            if (texto == null || texto.isBlank()) {
                log.warn("No se leyó la OC: el PDF no tiene texto");
                return LecturaOc.vacia();
            }
            return interpretar(texto);
        } catch (Exception e) {
            log.warn("No se pudo leer el PDF de la OC: {}", e.toString());
            return LecturaOc.vacia();
        }
    }

    public LecturaOc leer(InputStream pdf) {
        if (pdf == null) {
            return LecturaOc.vacia();
        }
        try {
            return leer(pdf.readAllBytes());
        } catch (Exception e) {
            log.warn("No se pudo leer el PDF de la OC: {}", e.toString());
            return LecturaOc.vacia();
        }
    }

    private static LecturaOc interpretar(String texto) {
        return new LecturaOc(numero(texto), fecha(texto), monto(texto), moneda(texto));
    }

    private static String numero(String texto) {
        Matcher matcher = NUMERO.matcher(texto);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static BigDecimal monto(String texto) {
        Matcher total = TOTAL.matcher(texto);
        if (!total.find()) {
            return null;
        }
        Matcher monto = MONTO.matcher(total.group(1));
        if (!monto.find()) {
            return null;
        }
        return parsearMonto(monto.group(1));
    }

    static BigDecimal parsearMonto(String raw) {
        int coma = raw.lastIndexOf(',');
        int punto = raw.lastIndexOf('.');
        int ultimo = Math.max(coma, punto);
        if (ultimo < 0) {
            return new BigDecimal(raw);
        }
        int decimales = raw.length() - ultimo - 1;
        String normalizado;
        if (decimales == 2) {
            char separador = raw.charAt(ultimo);
            char miles = separador == ',' ? '.' : ',';
            normalizado = raw.replace(String.valueOf(miles), "").replace(separador, '.');
        } else {
            normalizado = raw.replace(",", "").replace(".", "");
        }
        try {
            return new BigDecimal(normalizado).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String moneda(String texto) {
        Matcher total = TOTAL.matcher(texto);
        if (total.find()) {
            String junto = unaMoneda(total.group(0));
            if (junto != null) {
                return junto;
            }
        }
        boolean soles = tieneSoles(texto);
        boolean dolares = tieneDolares(texto);
        if (soles && !dolares) {
            return "PEN";
        }
        if (dolares && !soles) {
            return "USD";
        }
        return null;
    }

    private static String unaMoneda(String texto) {
        if (tieneSoles(texto) && !tieneDolares(texto)) {
            return "PEN";
        }
        if (tieneDolares(texto) && !tieneSoles(texto)) {
            return "USD";
        }
        return null;
    }

    private static boolean tieneSoles(String texto) {
        String t = texto.toUpperCase(Locale.ROOT);
        return t.contains("PEN") || t.contains("S/");
    }

    private static boolean tieneDolares(String texto) {
        String t = texto.toUpperCase(Locale.ROOT);
        return t.contains("USD") || t.contains("US$") || t.contains("$");
    }

    private static LocalDate fecha(String texto) {
        Matcher larga = FECHA_LARGA.matcher(texto);
        while (larga.find()) {
            LocalDate fecha = fechaLarga(larga.group(1), larga.group(2), larga.group(3));
            if (fecha != null) {
                return fecha;
            }
        }
        Matcher numerica = FECHA_NUM.matcher(texto);
        while (numerica.find()) {
            LocalDate fecha = fechaEstricta(numerica.group(1), numerica.group(2), numerica.group(3));
            if (fecha != null) {
                return fecha;
            }
        }
        return null;
    }

    private static LocalDate fechaLarga(String dia, String mes, String anio) {
        Integer numero = MESES.get(mes.toLowerCase(Locale.ROOT));
        if (numero == null) {
            return null;
        }
        return fechaEstricta(dia, String.valueOf(numero), anio);
    }

    private static LocalDate fechaEstricta(String dia, String mes, String anio) {
        try {
            return LocalDate.parse(
                    String.format("%02d/%02d/%s", Integer.parseInt(dia), Integer.parseInt(mes), anio),
                    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }
}
