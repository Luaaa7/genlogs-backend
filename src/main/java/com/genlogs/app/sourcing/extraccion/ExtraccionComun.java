package com.genlogs.app.sourcing.extraccion;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.genlogs.app.fuentes.config.CampoSelector;

/**
 * Extracción común (A4, v3.3): convierte los campos en texto que devuelve el
 * servicio de lectura (uno por ficha, según las reglas de fuente_selector) en
 * fichas limpias. La usan los 5 lectores, así todos entregan lo mismo.
 *
 * <ul>
 *   <li>Una ficha sin NOMBRE o sin ENLACE se descarta.</li>
 *   <li>Enlaces e imágenes relativos ("/itm/123") se completan con la URL base de la tienda.</li>
 *   <li>Un mismo enlace repetido cuenta una sola vez.</li>
 *   <li>Precio y moneda pasan por {@link NormalizadorPrecio}.</li>
 *   <li>Se devuelven como máximo {@code maximo} fichas (BUSQUEDA_MAX_RESULTADOS).</li>
 * </ul>
 */
public final class ExtraccionComun {

    private static final int LARGO_NOMBRE = 255;
    private static final int LARGO_DESCRIPCION = 2000;

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
        Set<String> enlacesVistos = new LinkedHashSet<>();
        for (Map<CampoSelector, String> campos : fichasEnTexto) {
            if (maximo > 0 && fichas.size() >= maximo) break;
            FichaExtraida ficha = ficha(campos, urlBase, monedaPorDefecto);
            if (ficha != null && enlacesVistos.add(ficha.enlace())) {
                fichas.add(ficha);
            }
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

    /** Texto sin espacios de más; null si queda vacío. */
    static String limpio(String texto) {
        if (texto == null) return null;
        String t = texto.replaceAll("[\\s\\u00A0\\u202F]+", " ").trim();
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