package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RF-77 · Captura por URL (Amazon, Alibaba y cualquier tienda).
 * Lee solo los metadatos públicos de la página: título, precio, moneda e imagen.
 * Si la tienda bloquea la lectura o no publica los datos, devuelve "bloqueada"
 * y el asistente llena todo a mano. Nunca lanza error por la tienda.
 */
@Slf4j
@Component
public class LectorUrl {

    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final int MAX_REDIRECCIONES = 3;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private static final Pattern LD_PRECIO = Pattern.compile("\"price\"\\s*:\\s*\"?([0-9][0-9.,]*)");
    private static final Pattern LD_MONEDA = Pattern.compile("\"priceCurrency\"\\s*:\\s*\"([A-Za-z]{3})\"");
    private static final Pattern LD_IMAGEN = Pattern.compile("\"image\"\\s*:\\s*\\[?\\s*\"(https?://[^\"]+)\"");

    /** Datos leídos. Todos pueden venir null; bloqueada = true si no se pudo leer nada útil. */
    public record DatosUrl(String url, String fuente, String titulo, BigDecimal precio,
                           String codigoMoneda, String urlImagen, boolean bloqueada, String motivo) {
    }

    public DatosUrl leer(String url) {
        URI actual = validarFormato(url);
        String fuente = detectarFuente(actual.getHost());
        try {
            // Las redirecciones se siguen a mano para revisar cada destino.
            for (int salto = 0; salto <= MAX_REDIRECCIONES; salto++) {
                validarDestino(actual);
                Connection.Response resp = Jsoup.connect(actual.toString())
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "es-PE,es;q=0.9,en;q=0.8")
                        .timeout(TIMEOUT_MS)
                        .maxBodySize(MAX_BYTES)
                        .followRedirects(false)
                        .execute();
                if (resp.statusCode() >= 300 && resp.statusCode() < 400 && resp.hasHeader("Location")) {
                    actual = validarFormato(actual.resolve(resp.header("Location")).toString());
                    continue;
                }
                return extraer(resp.parse(), actual.toString(), fuente);
            }
            return bloqueada(actual.toString(), fuente, "La página redirige demasiadas veces. Completa los datos a mano.");
        } catch (HttpStatusException e) {
            log.info("La tienda respondió {} al leer {}", e.getStatusCode(), url);
            return bloqueada(actual.toString(), fuente, "La tienda no permitió leer la página (HTTP "
                    + e.getStatusCode() + "). Completa los datos a mano.");
        } catch (IOException e) {
            log.info("No se pudo leer {}: {}", url, e.getMessage());
            return bloqueada(actual.toString(), fuente, "No se pudo abrir la página. Completa los datos a mano.");
        }
    }

    /** Separado de leer() para probarlo con HTML guardado, sin internet. */
    DatosUrl extraer(Document doc, String url, String fuente) {
        String titulo = primero(meta(doc, "og:title"), meta(doc, "twitter:title"), textoDe(doc.selectFirst("#productTitle")),
                doc.title());
        String imagen = primero(meta(doc, "og:image"), meta(doc, "twitter:image"),
                atributo(doc.selectFirst("#landingImage"), "abs:src"));
        String precioTxt = primero(meta(doc, "product:price:amount"), meta(doc, "og:price:amount"),
                atributo(doc.selectFirst("[itemprop=price]"), "content"),
                textoDe(doc.selectFirst("[itemprop=price]")));
        String moneda = primero(meta(doc, "product:price:currency"), meta(doc, "og:price:currency"),
                atributo(doc.selectFirst("[itemprop=priceCurrency]"), "content"));

        // JSON-LD de tipo Product (lo usan muchas tiendas)
        for (Element script : doc.select("script[type=\"application/ld+json\"]")) {
            String json = script.data();
            if (!json.contains("Product") && !json.contains("Offer")) continue;
            if (precioTxt == null) precioTxt = grupo(LD_PRECIO, json);
            if (moneda == null) moneda = grupo(LD_MONEDA, json);
            if (imagen == null) imagen = grupo(LD_IMAGEN, json);
        }

        BigDecimal precio = normalizarPrecio(precioTxt);
        String codigoMoneda = moneda != null ? moneda.trim().toUpperCase(Locale.ROOT) : null;
        boolean sinDatos = precio == null && imagen == null;
        return new DatosUrl(url, fuente, recortar(titulo), precio, codigoMoneda, imagen, sinDatos,
                sinDatos ? "La página no publica precio ni imagen. Completa los datos a mano." : null);
    }

    /** "1,234.56" → 1234.56 · "1.234,56" → 1234.56 · "18,50" → 18.50 · "US$ 25" → 25 */
    static BigDecimal normalizarPrecio(String texto) {
        if (texto == null) return null;
        String t = texto.replaceAll("[^0-9.,]", "");
        if (t.isEmpty()) return null;
        int coma = t.lastIndexOf(','), punto = t.lastIndexOf('.');
        if (coma > punto) {
            boolean decimalConComa = t.length() - coma - 1 <= 2;
            t = decimalConComa ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else {
            t = t.replace(",", "");
        }
        try {
            BigDecimal v = new BigDecimal(t);
            return v.signum() > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String detectarFuente(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (h.contains("amazon.")) return "AMAZON";
        if (h.contains("alibaba.") || h.contains("aliexpress.")) return "ALIBABA";
        if (h.contains("ebay.")) return "EBAY";
        if (h.contains("mercadolibre.") || h.contains("mercadolivre.")) return "MERCADO_LIBRE";
        return "PROVEEDOR";
    }

    /** Solo http/https con un sitio. No consulta la red. */
    static URI validarFormato(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("La URL no es válida");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = esquema.equals("http") || esquema.equals("https");
        if (!http || uri.getHost() == null) {
            throw new BusinessException("La URL debe empezar con http:// o https://");
        }
        return uri;
    }

    /** Nunca direcciones internas: evita que se use el servidor para leer la red interna. */
    static void validarDestino(URI uri) {
        try {
            for (InetAddress ip : InetAddress.getAllByName(uri.getHost())) {
                if (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress()
                        || ip.isAnyLocalAddress() || ip.isMulticastAddress()) {
                    throw new BusinessException("No se pueden leer direcciones internas");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException("No se encontró el sitio " + uri.getHost());
        }
    }

    // ---------- utilidades ----------

    private static DatosUrl bloqueada(String url, String fuente, String motivo) {
        return new DatosUrl(url, fuente, null, null, null, null, true, motivo);
    }

    private static String meta(Document doc, String nombre) {
        Element e = doc.selectFirst("meta[property=\"" + nombre + "\"], meta[name=\"" + nombre + "\"]");
        return e == null ? null : vacioANull(e.attr("content"));
    }

    private static String atributo(Element e, String attr) {
        return e == null ? null : vacioANull(e.attr(attr));
    }

    private static String textoDe(Element e) {
        return e == null ? null : vacioANull(e.text());
    }

    private static String grupo(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    private static String primero(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
