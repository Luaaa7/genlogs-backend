package com.genlogs.app.sourcing.extraccion;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.PaginaSelector;

/**
 * Reglas de extracción de una tienda, leídas de fuente_selector (A4, v3.3).
 * El lector se las manda al servicio de lectura en cada llamada.
 */
public record ReglasExtraccion(CodigoFuente fuente, Map<PaginaSelector, Map<CampoSelector, String>> reglas) {

    public ReglasExtraccion {
        Map<PaginaSelector, Map<CampoSelector, String>> copia = new EnumMap<>(PaginaSelector.class);
        if (reglas != null) {
            reglas.forEach((pagina, campos) -> copia.put(pagina,
                    Collections.unmodifiableMap(new EnumMap<>(campos))));
        }
        reglas = Collections.unmodifiableMap(copia);
    }

    /** Las reglas de una página (BUSQUEDA o FICHA); vacío si no tiene ninguna. */
    public Map<CampoSelector, String> de(PaginaSelector pagina) {
        return reglas.getOrDefault(pagina, Map.of());
    }

    /** La regla de un campo, o null si la tienda no la tiene. */
    public String regla(PaginaSelector pagina, CampoSelector campo) {
        return de(pagina).get(campo);
    }

    /**
     * Para buscar hacen falta, como mínimo, ITEM (dónde empieza cada resultado),
     * NOMBRE y ENLACE en la página de búsqueda. Sin ellas la tienda no se consulta.
     */
    public boolean sirveParaBuscar() {
        Map<CampoSelector, String> busqueda = de(PaginaSelector.BUSQUEDA);
        return busqueda.containsKey(CampoSelector.ITEM)
                && busqueda.containsKey(CampoSelector.NOMBRE)
                && busqueda.containsKey(CampoSelector.ENLACE);
    }
}