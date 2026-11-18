package com.genlogs.app.sourcing;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.genlogs.app.fuentes.config.FichaFuente;

/**
 * Normaliza las fichas del lector (BPMN 9-B.1 tarea A): precio vacío se queda vacío (nunca 0),
 * moneda en ISO de 3 letras, sin duplicar el producto, máximo 8.
 *
 * <p>Un mismo producto cuenta una sola vez aunque el enlace cambie solo en los parámetros
 * de rastreo (ref, utm_*, pd_rd_w...): así un patrocinado de Amazon no sale dos veces. Los
 * parámetros que dicen qué producto es (pd_rd_i, url, id...) se respetan.</p>
 */
final class NormalizadorFicha {

    static final int MAX_FICHAS = 8;

    /** Rastreo común a cualquier sitio. */
    private static final Set<String> RASTREO_GLOBAL = Set.of(
            "gclid", "fbclid", "srsltid", "msclkid", "mc_cid", "mc_eid");

    /** Rastreo propio de cada sitio (se busca dentro del host). */
    private static final Map<String, Set<String>> RASTREO_POR_SITIO = Map.of(
            "amazon", Set.of("ref", "ref_", "qid", "sr", "sprefix", "crid", "keywords", "dib", "dib_tag",
                    "th", "psc", "spla", "sp_csd", "spc", "ie", "content-id", "smid", "linkcode", "tag",
                    "camp", "creative"),
            "ebay", Set.of("_skw", "itmmeta", "hash", "itmprp", "_trksid", "_trkparms", "_from",
                    "mkcid", "mkevt", "campid", "customid"),
            "aliexpress", Set.of("spm", "scm", "algo_pvid", "algo_exp_id", "pvid", "utparam", "pdp_npi",
                    "pdp_ext_f", "tracking_id", "searchvariation"));

    private NormalizadorFicha() {
    }

    static List<ResultadoBusqueda> normalizar(List<FichaFuente> fichas, ConsultaBusqueda consulta) {
        if (fichas == null || fichas.isEmpty()) {
            return List.of();
        }
        List<ResultadoBusqueda> out = new ArrayList<>();
        Set<String> vistos = new LinkedHashSet<>();
        for (FichaFuente f : fichas) {
            if (f == null) {
                continue;
            }
            String enlace = texto(f.enlace());
            String nombre = texto(f.nombre());
            if (nombre == null && enlace == null) {
                continue;
            }
            String clave = enlace != null ? claveEnlace(enlace) : nombre + "|" + texto(f.precio());
            if (!vistos.add(clave)) {
                continue;
            }
            out.add(new ResultadoBusqueda(
                    nombre,
                    consulta == null ? null : consulta.marca(),
                    consulta == null ? null : consulta.numeroParte(),
                    precio(f.precio()),
                    moneda(f.moneda()),
                    enlace,
                    texto(f.imagen()),
                    texto(f.descripcion())));
            if (out.size() == MAX_FICHAS) {
                break;
            }
        }
        return List.copyOf(out);
    }

    /** Vacío o no numérico → null. Nunca BigDecimal.ZERO. */
    static BigDecimal precio(String texto) {
        return LectorUrl.normalizarPrecio(texto);
    }

    static String moneda(String texto) {
        String t = texto(texto);
        if (t == null) {
            return null;
        }
        t = t.toUpperCase(Locale.ROOT);
        return t.length() == 3 ? t : null;
    }

    /**
     * Qué producto es el enlace, para no repetirlo: sin "www.", sin "#...", sin "/" final, sin
     * "/ref=..." en la ruta y sin los parámetros de rastreo del sitio; los demás se ordenan.
     * El valor de "url=" (típico de /sspa/click) se decodifica y se normaliza igual.
     * Si el enlace no se puede leer como URL, se compara tal cual.
     */
    static String claveEnlace(String enlace) {
        try {
            return clave(enlace, 0, "");
        } catch (Exception e) {
            return enlace;
        }
    }

    private static String clave(String enlace, int profundidad, String hostPadre) throws URISyntaxException {
        URI uri = new URI(enlace);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String hostEfectivo = host.isEmpty() ? hostPadre : host;
        String camino = limpiarCamino(uri.getRawPath() == null ? "" : uri.getRawPath());
        Set<String> parametros = new TreeSet<>();
        String consulta = uri.getRawQuery();
        if (consulta != null) {
            for (String par : consulta.split("&")) {
                if (par.isEmpty()) {
                    continue;
                }
                String nombre = par.split("=", 2)[0].toLowerCase(Locale.ROOT);
                if (esRastreo(nombre, hostEfectivo)) {
                    continue;
                }
                if (nombre.equals("url") && par.contains("=") && profundidad < 2) {
                    par = "url=" + claveInterna(par.substring(par.indexOf('=') + 1), profundidad, hostEfectivo);
                }
                parametros.add(par);
            }
        }
        return host + camino + (parametros.isEmpty() ? "" : "?" + String.join("&", parametros));
    }

    /** Normaliza el valor de url=; si no se puede, lo deja como venía. */
    private static String claveInterna(String valor, int profundidad, String host) {
        try {
            String decodificado = URLDecoder.decode(valor, StandardCharsets.UTF_8);
            return URLEncoder.encode(clave(decodificado, profundidad + 1, host), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return valor;
        }
    }

    private static String limpiarCamino(String camino) {
        StringBuilder sb = new StringBuilder();
        for (String segmento : camino.split("/")) {
            if (segmento.isEmpty() || segmento.toLowerCase(Locale.ROOT).startsWith("ref=")) {
                continue;
            }
            sb.append('/').append(segmento);
        }
        return sb.toString();
    }

    private static boolean esRastreo(String nombre, String host) {
        if (nombre.startsWith("utm_") || RASTREO_GLOBAL.contains(nombre)) {
            return true;
        }
        for (Map.Entry<String, Set<String>> sitio : RASTREO_POR_SITIO.entrySet()) {
            if (host.contains(sitio.getKey()) && sitio.getValue().contains(nombre)) {
                return true;
            }
        }
        return host.contains("amazon")
                && (nombre.startsWith("pf_rd_") || (nombre.startsWith("pd_rd_") && !nombre.equals("pd_rd_i")));
    }

    private static String texto(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}