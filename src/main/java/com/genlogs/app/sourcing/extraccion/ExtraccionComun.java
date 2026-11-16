package com.genlogs.app.sourcing.extraccion;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genlogs.app.fuentes.config.CampoSelector;

/**
 * Extracción común (A4, v3.3): convierte los campos en texto que devuelve el
 * servicio de lectura (uno por ficha, según las reglas de fuente_selector) en
 * fichas limpias. La usan los 5 lectores, así todos entregan lo mismo.
 *
 * <ul>
 *   <li>Una ficha sin NOMBRE o sin ENLACE se descarta.</li>
 *   <li>Enlaces e imágenes relativos ("/itm/123") se completan con la URL base de la tienda.</li>
 *   <li>Un mismo producto repetido cuenta una sola vez: se ignoran solo los parámetros de
 *       rastreo (ref, utm_*, pd_rd_w...), así los patrocinados no se repiten; los que dicen
 *       qué producto es (pd_rd_i, url, id...) se respetan.</li>
 *   <li>Las fichas descartadas se cuentan en el log, para notar una regla rota.</li>
 *   <li>Precio y moneda pasan por {@link NormalizadorPrecio}.</li>
 *   <li>Se devuelven como máximo {@code maximo} fichas (BUSQUEDA_MAX_RESULTADOS).</li>
 * </ul>
 */
public final class ExtraccionComun {

    private static final Logger log = LoggerFactory.getLogger(ExtraccionComun.class);

    private static final int LARGO_NOMBRE = 255;
    private static final int LARGO_DESCRIPCION = 2000;

    /** Parámetros de rastreo: cambian entre un resultado normal y uno patrocinado del mismo producto. */
    private static final Set<String> RASTREO = Set.of(
            "ref", "ref_", "qid", "sr", "sprefix", "crid", "keywords", "dib", "dib_tag", "th", "psc",
            "spla", "sp_csd", "spc", "ie", "content-id", "smid", "linkcode", "tag", "camp", "creative",
            "_skw", "itmmeta", "hash", "itmprp", "_trksid", "_trkparms", "_from", "mkcid", "mkevt",
            "spm", "scm", "algo_pvid", "algo_exp_id", "pvid", "utparam", "pdp_npi", "pdp_ext_f",
            "gclid", "fbclid", "srsltid", "tracking_id", "searchvariation");

    private ExtraccionComun() {
    }

    /**
     * @param fichasEnTexto    lo que leyó el servicio de lectura, una entrada por ficha
     * @param urlBase          url_base de la tienda (fuente_busqueda), para completar enlaces relativos
     * @param monedaPorDefecto moneda de la tienda si la ficha no la dice (p. ej. USD en eBay)
     * @param maximo           fichas como máximo; 0 o menos = sin límite
     */
    public static List<FichaExtraida> fichas(List<Map<CampoSelector, String>> fichasEnTexto, String urlBase,
                                             String monedaPorDefecto, int maximo) {
        List<FichaExtraida> fichas = new ArrayList<>();
        if (fichasEnTexto == null) return fichas;
        Set<String> productosVistos = new LinkedHashSet<>();
        int incompletas = 0;
        int repetidas = 0;
        for (Map<CampoSelector, String> campos : fichasEnTexto) {
            if (maximo > 0 && fichas.size() >= maximo) break;
            FichaExtraida ficha = ficha(campos, urlBase, monedaPorDefecto);
            if (ficha == null) {
                incompletas++;
            } else if (!productosVistos.add(claveEnlace(ficha.enlace()))) {
                repetidas++;
            } else {
                fichas.add(ficha);
            }
        }
        if (incompletas > 0 || repetidas > 0) {
            log.info("Extracción en {}: {} fichas, {} descartadas sin nombre o enlace, {} repetidas",
                    urlBase, fichas.size(), incompletas, repetidas);
        }
        return fichas;
    }

    /** Una ficha limpia, o null si le falta el nombre o el enlace. */
    public static FichaExtraida ficha(Map<CampoSelector, String> campos, String urlBase, String monedaPorDefecto) {
        if (campos == null) return null;
        String nombre = recortar(limpio(campos.get(CampoSelector.NOMBRE)), LARGO_NOMBRE);
        String enlace = absoluta(limpio(campos.get(CampoSelector.ENLACE)), urlBase);
        if (nombre == null || enlace == null) return null;
        PrecioExtraido precio = NormalizadorPrecio.normalizar(
                campos.get(CampoSelector.PRECIO), campos.get(CampoSelector.MONEDA), monedaPorDefecto);
        return new FichaExtraida(
                nombre,
                recortar(limpio(campos.get(CampoSelector.DESCRIPCION)), LARGO_DESCRIPCION),
                precio.monto(),
                precio.codigoMoneda(),
                absoluta(limpio(campos.get(CampoSelector.IMAGEN)), urlBase),
                enlace);
    }

    /**
     * Qué producto es el enlace, para no repetirlo: sin "www.", sin "#..." y sin los parámetros
     * de rastreo; los demás parámetros se ordenan. Así "…/dp/B01?ref=sr_1" y "…/dp/B01?ref=sspa"
     * son el mismo, pero "…?pd_rd_i=B01" y "…?pd_rd_i=B02" son dos productos.
     */
    static String claveEnlace(String enlace) {
        try {
            URI uri = new URI(enlace);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            String camino = uri.getRawPath() == null ? "" : uri.getRawPath();
            Set<String> parametros = new TreeSet<>();
            String consulta = uri.getRawQuery();
            if (consulta != null) {
                for (String par : consulta.split("&")) {
                    if (par.isEmpty()) continue;
                    String nombre = par.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    if (!esRastreo(nombre)) parametros.add(par);
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

    /** Texto sin espacios de más (incluidos los Unicode); null si queda vacío. */
    static String limpio(String texto) {
        if (texto == null) return null;
        String t = texto.replaceAll("[\\s\\p{Zs}]+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    private static String recortar(String texto, int largo) {
        if (texto == null || texto.length() <= largo) return texto;
        return texto.substring(0, largo).trim();
    }

    /** URL completa: "//x.com/a" y "/itm/1" se completan con la base; solo se aceptan http y https. */
    static String absoluta(String url, String urlBase) {
        if (url == null) return null;
        try {
            URI uri = new URI(url.replace(" ", "%20"));
            if (!uri.isAbsolute()) {
                if (urlBase == null || urlBase.isBlank()) return null;
                uri = new URI(urlBase.trim()).resolve(uri);
            }
            String esquema = uri.getScheme();
            if (esquema == null) return null;
            esquema = esquema.toLowerCase();
            return esquema.equals("http") || esquema.equals("https") ? uri.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}