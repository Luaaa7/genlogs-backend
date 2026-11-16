package com.genlogs.app.fuentes.lectores;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;

/**
 * ME (v3.3) · Pasa los selectores de fuente_selector (los que manda el despachador) al
 * formato de reglas del servicio de lectura (contrato P1/P2 de Daniel). Sirve para los
 * 5 lectores, no solo para eBay.
 *
 * <ul>
 *   <li>ITEM se envía como "contenedor" (el bloque de cada resultado).</li>
 *   <li>NOMBRE, DESCRIPCION, PRECIO, MONEDA, IMAGEN y ENLACE se envían como
 *       {selector, atributo}. El atributo se escribe al final de la regla con @:
 *       "a.s-item__link@href", "img@src".</li>
 *   <li>codigoMoneda y separadorDecimal los pone el lector de cada tienda.</li>
 * </ul>
 */
public final class ReglasParaLector {

    /** Atributos que acepta el servicio de lectura. */
    static final Set<String> ATRIBUTOS = Set.of("content", "href", "src", "data-src");

    private ReglasParaLector() {
    }

    /**
     * @param selectores       reglas activas de la tienda (las manda el despachador)
     * @param pagina           BUSQUEDA o FICHA: solo se usan las reglas de esa página
     * @param codigoMoneda     ISO de la moneda si la página no la muestra (p. ej. USD); puede ser null
     * @param separadorDecimal "." o ","; puede ser null
     */
    public static Map<String, Object> de(List<SelectorFuenteDto> selectores, PaginaSelector pagina,
                                         String codigoMoneda, String separadorDecimal) {
        Map<String, Object> reglas = new LinkedHashMap<>();
        if (selectores != null) {
            for (SelectorFuenteDto s : selectores) {
                if (s == null || s.pagina() != pagina || s.campo() == null
                        || s.regla() == null || s.regla().isBlank()) {
                    continue;
                }
                if (s.campo() == CampoSelector.ITEM) {
                    reglas.put("contenedor", campo(s.regla()).get("selector"));
                } else {
                    reglas.put(s.campo().name().toLowerCase(Locale.ROOT), campo(s.regla()));
                }
            }
        }
        if (codigoMoneda != null && !codigoMoneda.isBlank()) {
            reglas.put("codigoMoneda", codigoMoneda.trim().toUpperCase(Locale.ROOT));
        }
        if (separadorDecimal != null && !separadorDecimal.isBlank()) {
            reglas.put("separadorDecimal", separadorDecimal.trim());
        }
        return reglas;
    }

    /** Para buscar, el servicio de lectura exige contenedor (ITEM), nombre, precio y enlace. */
    public static boolean sirveParaBuscar(Map<String, Object> reglas) {
        return reglas.containsKey("contenedor") && reglas.containsKey("nombre")
                && reglas.containsKey("precio") && reglas.containsKey("enlace");
    }

    /** Para leer una ficha, exige nombre y precio. */
    public static boolean sirveParaFicha(Map<String, Object> reglas) {
        return reglas.containsKey("nombre") && reglas.containsKey("precio");
    }

    /** "img@src" → {selector: "img", atributo: "src"}; sin @ (o con un atributo no permitido) solo selector. */
    static Map<String, Object> campo(String regla) {
        Map<String, Object> campo = new LinkedHashMap<>();
        String texto = regla.trim();
        int arroba = texto.lastIndexOf('@');
        if (arroba > 0) {
            String atributo = texto.substring(arroba + 1).trim().toLowerCase(Locale.ROOT);
            if (ATRIBUTOS.contains(atributo)) {
                campo.put("selector", texto.substring(0, arroba).trim());
                campo.put("atributo", atributo);
                return campo;
            }
        }
        campo.put("selector", texto);
        return campo;
    }
}