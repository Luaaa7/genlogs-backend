package com.genlogs.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatos numéricos de los PDF. Usan siempre el mismo estilo que la pantalla
 * (miles con coma, decimales con punto), sin depender del idioma del servidor.
 */
public final class FormatoPdf {

    private FormatoPdf() {
    }

    /**
     * Cantidad sin ceros sobrantes: 1.000 -> "1", 4.000 -> "4", 2.500 -> "2.5", 1200 -> "1,200".
     * Admite hasta 3 decimales.
     */
    public static String cantidad(BigDecimal valor) {
        if (valor == null) {
            return "0";
        }
        return nuevoFormato("#,##0.###").format(valor);
    }

    /** Importe con 2 decimales y separador de miles, sin moneda: 4209.3 -> "4,209.30". */
    public static String importe(BigDecimal valor) {
        return nuevoFormato("#,##0.00").format(valor != null ? valor : BigDecimal.ZERO);
    }

    /** Importe precedido del código ISO de la moneda: ("USD", 13018) -> "USD 13,018.00". */
    public static String monto(String codigoMoneda, BigDecimal valor) {
        String codigo = codigoMoneda == null || codigoMoneda.isBlank() ? "PEN" : codigoMoneda.trim().toUpperCase(Locale.ROOT);
        return codigo + " " + importe(valor);
    }

    // DecimalFormat no es thread-safe: se crea uno por llamada.
    private static DecimalFormat nuevoFormato(String patron) {
        DecimalFormat df = new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df;
    }
}
