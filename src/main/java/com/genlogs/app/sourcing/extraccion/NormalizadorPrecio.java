package com.genlogs.app.sourcing.extraccion;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción común (A4, v3.3): convierte el texto de precio que trae cualquier
 * tienda en un monto y una moneda ISO. La usan los 5 lectores (eBay, Mercado
 * Libre, Amazon, AliExpress y Alibaba), así todos devuelven el precio igual.
 *
 * <ul>
 *   <li>Sin precio (vacío, "Consultar", "Gratis", 0) devuelve monto null, nunca 0.</li>
 *   <li>Un precio negativo ("-$5.00") no es precio: monto null.</li>
 *   <li>Un porcentaje ("20% off") no es precio: se salta y se toma el número siguiente.</li>
 *   <li>Un rango ("US $12.50 a US $15.00") toma el primer precio.</li>
 *   <li>Separadores: "1,234.50", "1.234,50", "12,50" y "1 234,50" dan el mismo número.
 *       Con 3 dígitos después del último separador es de miles ("1,234" = 1234), salvo
 *       que la parte entera sea 0 ("0.004" = 0.004).</li>
 *   <li>Nunca se redondea a 0: el monto tiene al menos 2 decimales y conserva los que traiga.</li>
 *   <li>Espacios Unicode (no separable, fino) cuentan como espacio: "US\u00A0$45" es USD.</li>
 *   <li>La moneda sale del texto de moneda; si no hay, del precio; si tampoco, de la
 *       moneda por defecto de la fuente (solo si es un código ISO válido).</li>
 *   <li>Una moneda escrita que no se reconoce ("Bs.", "₹", "XYZ") deja la moneda vacía:
 *       nunca se cambia por la moneda por defecto.</li>
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

    /** Símbolos que ya entiende el mapa; cualquier otro símbolo de moneda es una moneda desconocida. */
    private static final Set<Character> SIMBOLOS_CONOCIDOS = Set.of('$', '€', '£', '¥');

    /** Códigos ISO que no son una moneda real. */
    private static final Set<String> NO_SON_MONEDA = Set.of("XXX", "XTS");

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

    /** Primer número del texto, con sus separadores de miles y decimales (incluye espacios Unicode). */
    private static final Pattern NUMERO = Pattern.compile("\\d[\\d.,\\s\\p{Zs}]*");

    /** Espacios, incluidos los Unicode (U+00A0, U+202F, U+2009...). */
    private static final Pattern ESPACIOS = Pattern.compile("[\\s\\p{Zs}]+");

    /** Lo que puede ir entre un signo menos y el número: espacios, símbolo y letras de moneda. */
    private static final Pattern ANTES_DEL_NUMERO = Pattern.compile("[\\s\\p{Zs}\\p{Sc}A-Za-z/.]*$");

    /** Código de 3 letras pegado al número: "JPY 1,000" o "1,000 JPY". */
    private static final Pattern ISO_ANTES = Pattern.compile("(?<![A-Z])([A-Z]{3})[\\s\\p{Zs}]*\\p{Sc}?[\\s\\p{Zs}]*$");
    private static final Pattern ISO_DESPUES = Pattern.compile("^[\\s\\p{Zs}]*([A-Z]{3})(?![A-Z])");

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
        Numero numero = primerPrecio(textoPrecio);
        if (numero == null) {
            return PrecioExtraido.VACIO;
        }
        boolean dolarOPeso = textoPrecio.contains(DOLAR_O_PESO);
        if (textoMoneda != null && !textoMoneda.isBlank()) {
            String dicha = moneda(textoMoneda);
            boolean soloDolar = textoMoneda.trim().equals(DOLAR_O_PESO);
            if (dicha != null || !soloDolar) {
                // La tienda dijo la moneda: si no se entiende, queda vacía (no se cambia por la de la fuente).
                return new PrecioExtraido(numero.monto(), dicha);
            }
            dolarOPeso = true;
        }
        String moneda = moneda(textoPrecio);
        if (moneda == null) {
            moneda = isoJuntoAlNumero(textoPrecio, numero);
        }
        if (moneda == null && tieneSimboloDesconocido(textoPrecio)) {
            return new PrecioExtraido(numero.monto(), null);
        }
        if (moneda == null) {
            String porDefecto = iso(monedaPorDefecto);
            if (porDefecto != null) {
                moneda = porDefecto;
            } else if (dolarOPeso) {
                moneda = "USD";
            }
        }
        return new PrecioExtraido(numero.monto(), moneda);
    }

    /**
     * ISO de la moneda que aparece en el texto, o null si no aparece ninguna conocida
     * (un "$" solo también da null). Un texto que es solo un código ISO válido ("JPY") lo devuelve.
     */
    public static String moneda(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = ESPACIOS.matcher(texto.toUpperCase(Locale.ROOT)).replaceAll(" ").trim();
        for (Map.Entry<Pattern, String> m : PATRONES.entrySet()) {
            if (m.getKey().matcher(t).find()) return m.getValue();
        }
        return iso(t);
    }

    /** El código si es una moneda ISO 4217 real ("usd" → "USD"); null si no lo es. */
    public static String iso(String codigo) {
        if (codigo == null) return null;
        String c = codigo.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{3}") || NO_SON_MONEDA.contains(c)) return null;
        try {
            Currency.getInstance(c);
            return c;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Monto con al menos 2 decimales, o null si el texto no trae un precio mayor que 0. */
    public static BigDecimal monto(String texto) {
        Numero n = primerPrecio(texto);
        return n == null ? null : n.monto();
    }

    // ---------------------------------------------------------------- apoyo

    private record Numero(BigDecimal monto, int inicio, int fin) {
    }

    /** El primer número que es precio: salta porcentajes; un número negativo no es precio. */
    private static Numero primerPrecio(String texto) {
        if (texto == null || texto.isBlank()) return null;
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            if (esPorcentaje(texto, m.end())) {
                continue;
            }
            if (esNegativo(texto.substring(0, m.start()))) {
                return null;
            }
            BigDecimal valor = numero(m.group());
            return valor == null ? null : new Numero(valor, m.start(), m.end());
        }
        return null;
    }

    private static boolean esPorcentaje(String texto, int fin) {
        for (int i = fin; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '%') return true;
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
        }
        return false;
    }

    private static boolean esNegativo(String antes) {
        String sinMoneda = ANTES_DEL_NUMERO.matcher(antes).replaceAll("");
        return sinMoneda.endsWith("-") || sinMoneda.endsWith("\u2212");
    }

    private static BigDecimal numero(String grupo) {
        String numero = ESPACIOS.matcher(grupo).replaceAll("").replaceAll("[.,]+$", "");
        if (numero.isEmpty()) return null;
        try {
            BigDecimal valor = new BigDecimal(conPuntoDecimal(numero));
            if (valor.signum() <= 0) return null;
            return valor.scale() < 2 ? valor.setScale(2) : valor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Deja el número con punto decimal y sin separador de miles.
     * El último separador es el decimal si le siguen 1 o 2 dígitos; si le siguen 3,
     * es de miles ("1,234" = 1234 y "1.234.567" = 1234567), salvo que la parte entera
     * sea 0 ("0.004" y "0,004" = 0.004).
     */
    private static String conPuntoDecimal(String numero) {
        int ultimo = Math.max(numero.lastIndexOf(','), numero.lastIndexOf('.'));
        if (ultimo < 0) return numero;
        int digitosDespues = numero.length() - ultimo - 1;
        String entera = numero.substring(0, ultimo).replaceAll("[.,]", "");
        boolean enteraCero = entera.isEmpty() || entera.matches("0+");
        if (digitosDespues == 3 && !enteraCero) {
            return numero.replaceAll("[.,]", "");
        }
        return (entera.isEmpty() ? "0" : entera) + "." + numero.substring(ultimo + 1);
    }

    /** "JPY 1,000" o "1,000 JPY": un código ISO válido pegado al número. */
    private static String isoJuntoAlNumero(String texto, Numero n) {
        String antes = texto.substring(0, n.inicio()).toUpperCase(Locale.ROOT);
        Matcher a = ISO_ANTES.matcher(antes);
        if (a.find() && iso(a.group(1)) != null) return a.group(1);
        String despues = texto.substring(n.fin()).toUpperCase(Locale.ROOT);
        Matcher d = ISO_DESPUES.matcher(despues);
        if (d.find() && iso(d.group(1)) != null) return d.group(1);
        return null;
    }

    /** ¿Trae un símbolo de moneda que no está en el mapa (₹, ₩, ₱...)? */
    private static boolean tieneSimboloDesconocido(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (Character.getType(c) == Character.CURRENCY_SYMBOL && !SIMBOLOS_CONOCIDOS.contains(c)) {
                return true;
            }
        }
        return false;
    }
}