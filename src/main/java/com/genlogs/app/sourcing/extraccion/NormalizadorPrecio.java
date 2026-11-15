package com.genlogs.app.sourcing.extraccion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción común (A4, v3.3): convierte el texto de precio que trae cualquier
 * tienda en un monto y una moneda ISO. La usan los 5 lectores (eBay, Mercado
 * Libre, Amazon, AliExpress y Alibaba), así todos devuelven el precio igual.
 *
 * <ul>
 *   <li>Sin precio (vacío, "Consultar", "Gratis", 0) devuelve monto null, nunca 0.</li>
 *   <li>Un rango ("US $12.50 a US $15.00") toma el primer precio.</li>
 *   <li>Separadores: "1,234.50", "1.234,50", "12,50" y "1 234,50" dan el mismo número.</li>
 *   <li>La moneda sale del texto de moneda; si no hay, del símbolo del precio;
 *       si tampoco, de la moneda por defecto de la fuente.</li>
 *   <li>Un "$" solo es ambiguo (dólar, peso mexicano, peso argentino...): se usa la moneda
 *       por defecto de la fuente y, si no hay, USD. MX$, CA$, R$, AU$... dan su moneda.</li>
 * </ul>
 */
public final class NormalizadorPrecio {

    /** "$" solo: puede ser dólar o peso; decide la moneda por defecto de la fuente. */
    private static final String DOLAR_O_PESO = "$";

    /** Símbolos y nombres de moneda → ISO. Los más largos van primero ("US$" antes que "$"). */
    private static final Map<String, String> MONEDAS = new LinkedHashMap<>();

    static {
        MONEDAS.put("MX$", "MXN");
        MONEDAS.put("CA$", "CAD");
        MONEDAS.put("C$", "CAD");
        MONEDAS.put("AU$", "AUD");
        MONEDAS.put("A$", "AUD");
        MONEDAS.put("NZ$", "NZD");
        MONEDAS.put("HK$", "HKD");
        MONEDAS.put("R$", "BRL");
        MONEDAS.put("US$", "USD");
        MONEDAS.put("US $", "USD");
        MONEDAS.put("USD", "USD");
        MONEDAS.put("DÓLARES", "USD");
        MONEDAS.put("DOLARES", "USD");
        MONEDAS.put("DOLLARS", "USD");
        MONEDAS.put("S/.", "PEN");
        MONEDAS.put("S/", "PEN");
        MONEDAS.put("PEN", "PEN");
        MONEDAS.put("SOLES", "PEN");
        MONEDAS.put("EUR", "EUR");
        MONEDAS.put("€", "EUR");
        MONEDAS.put("GBP", "GBP");
        MONEDAS.put("£", "GBP");
        MONEDAS.put("CN¥", "CNY");
        MONEDAS.put("CNY", "CNY");
        MONEDAS.put("RMB", "CNY");
        MONEDAS.put("¥", "CNY");
        MONEDAS.put("MXN", "MXN");
        MONEDAS.put("CAD", "CAD");
        MONEDAS.put("AUD", "AUD");
        MONEDAS.put("BRL", "BRL");
        MONEDAS.put("ARS", "ARS");
        MONEDAS.put("CLP", "CLP");
        MONEDAS.put("COP", "COP");
    }

    /**
     * Las claves de solo letras ("USD", "CAD") se buscan como palabra completa, para que
     * "cada" no sea CAD ni "dollars" sea ARS; las que llevan símbolo se buscan tal cual.
     */
    private static final Map<Pattern, String> PATRONES = new LinkedHashMap<>();

    static {
        MONEDAS.forEach((clave, iso) -> PATRONES.put(clave.matches("[A-ZÓ]+")
                ? Pattern.compile("(?<![A-ZÓ])" + clave + "(?![A-ZÓ])")
                : Pattern.compile(Pattern.quote(clave)), iso));
    }

    /** Primer número del texto, con sus separadores de miles y decimales. */
    private static final Pattern NUMERO = Pattern.compile("\\d[\\d.,\\s\\u00A0\\u202F]*");

    private NormalizadorPrecio() {
    }

    /** Igual que {@link #normalizar(String, String, String)} sin texto de moneda ni moneda por defecto. */
    public static PrecioExtraido normalizar(String textoPrecio) {
        return normalizar(textoPrecio, null, null);
    }

    /**
     * @param textoPrecio      lo que dice la tienda: "US $1,234.50", "S/ 99.90", "12,50 €"
     * @param textoMoneda      la moneda si la tienda la trae aparte ("USD", "S/"); puede ser null
     * @param monedaPorDefecto ISO a usar si el texto no dice la moneda (p. ej. USD en eBay); puede ser null
     */
    public static PrecioExtraido normalizar(String textoPrecio, String textoMoneda, String monedaPorDefecto) {
        BigDecimal monto = monto(textoPrecio);
        if (monto == null) {
            return PrecioExtraido.VACIO;
        }
        String moneda = moneda(textoMoneda);
        if (moneda == null) moneda = moneda(textoPrecio);
        boolean dolarOPeso = moneda == null && (tieneDolarSolo(textoMoneda) || tieneDolarSolo(textoPrecio));
        if (moneda == null && monedaPorDefecto != null && !monedaPorDefecto.isBlank()) {
            moneda = monedaPorDefecto.trim().toUpperCase(Locale.ROOT);
        }
        if (moneda == null && dolarOPeso) moneda = "USD";
        return new PrecioExtraido(monto, moneda);
    }

    private static boolean tieneDolarSolo(String texto) {
        return texto != null && texto.contains(DOLAR_O_PESO);
    }

    /** ISO de la moneda que aparece en el texto, o null si no aparece ninguna (un "$" solo también da null). */
    public static String moneda(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.toUpperCase(Locale.ROOT);
        for (Map.Entry<Pattern, String> m : PATRONES.entrySet()) {
            if (m.getKey().matcher(t).find()) return m.getValue();
        }
        return null;
    }

    /** Monto con 2 decimales, o null si el texto no trae un precio mayor que 0. */
    public static BigDecimal monto(String texto) {
        if (texto == null || texto.isBlank()) return null;
        Matcher m = NUMERO.matcher(texto);
        if (!m.find()) return null;
        String numero = m.group().replaceAll("[\\s\\u00A0\\u202F]", "");
        numero = numero.replaceAll("[.,]+$", "");
        if (numero.isEmpty()) return null;
        try {
            BigDecimal valor = new BigDecimal(conPuntoDecimal(numero));
            return valor.signum() > 0 ? valor.setScale(2, RoundingMode.HALF_UP) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Deja el número con punto decimal y sin separador de miles.
     * El último separador es el decimal si le siguen 1 o 2 dígitos; si le siguen 3,
     * es de miles ("1,234" = 1234 y "1.234.567" = 1234567).
     */
    private static String conPuntoDecimal(String numero) {
        int ultimo = Math.max(numero.lastIndexOf(','), numero.lastIndexOf('.'));
        if (ultimo < 0) return numero;
        int digitosDespues = numero.length() - ultimo - 1;
        if (digitosDespues == 3) {
            return numero.replaceAll("[.,]", "");
        }
        String entera = numero.substring(0, ultimo).replaceAll("[.,]", "");
        return entera + "." + numero.substring(ultimo + 1);
    }
}