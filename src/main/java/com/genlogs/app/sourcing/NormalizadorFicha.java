package com.genlogs.app.sourcing;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    /** Parámetros de rastreo: cambian entre un resultado normal y uno patrocinado del mismo producto. */
    private static final Set<String> RASTREO = Set.of(
            "ref", "ref_", "qid", "sr", "sprefix", "crid", "keywords", "dib", "dib_tag", "th", "psc",
            "spla", "sp_csd", "spc", "ie", "content-id", "smid", "linkcode", "tag", "camp", "creative",
            "_skw", "itmmeta", "hash", "itmprp", "_trksid", "_trkparms", "_from", "mkcid", "mkevt",
            "spm", "scm", "algo_pvid", "algo_exp_id", "pvid", "utparam", "pdp_npi", "pdp_ext_f",
            "gclid", "fbclid", "srsltid", "tracking_id", "searchvariation");

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
     * Qué producto es el enlace, para no repetirlo: sin "www.", sin "#..." y sin los parámetros
     * de rastreo; los demás parámetros se ordenan. Así "…/dp/B01?ref=sr_1" y "…/dp/B01?ref=sspa"
     * son el mismo, pero "…?pd_rd_i=B01" y "…?pd_rd_i=B02" son dos productos.
     * Si el enlace no se puede leer como URL, se compara tal cual.
     */
    static String claveEnlace(String enlace) {
        try {
            URI uri = new URI(enlace);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String camino = uri.getRawPath() == null ? "" : uri.getRawPath();
            Set<String> parametros = new TreeSet<>();
            String consulta = uri.getRawQuery();
            if (consulta != null) {
                for (String par : consulta.split("&")) {
                    if (par.isEmpty()) {
                        continue;
                    }
                    String nombre = par.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    if (!esRastreo(nombre)) {
                        parametros.add(par);
                    }
                }
            }
            return host + camino + (parametros.isEmpty() ? "" : "?" + String.join("&", parametros));
        } catch (Exception e) {
            return enlace;
        }
    }

    private static boolean esRastreo(String nombre) {
        return RASTREO.contains(nombre) || nombre.startsWith("utm_") || nombre.startsWith("pf_rd_")
                || (nombre.startsWith("pd_rd_") && !nombre.equals("pd_rd_i"));
    }

    private static String texto(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}