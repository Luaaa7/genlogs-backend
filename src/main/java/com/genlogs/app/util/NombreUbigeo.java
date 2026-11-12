package com.genlogs.app.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Normaliza nombres de departamento, provincia y distrito para compararlos con
 * el catálogo del INEI (D-15). Factiliza y la base escriben el mismo lugar de
 * formas distintas: con o sin tildes, en mayúsculas, con puntos ("PROV. CONST.
 * DEL CALLAO") o con otra ortografía ("NASCA" / "Nazca").
 *
 * Usa las mismas equivalencias que v3_2_ubigeo.sql (sin tildes, mayúsculas y
 * NASCA = NAZCA), para que el backend y la migración reconozcan lo mismo.
 */
public final class NombreUbigeo {

    /** Ortografías distintas de un mismo nombre: se llevan a la que usa el INEI. */
    private static final Map<String, String> EQUIVALENCIAS = Map.of(
            "NASCA", "NAZCA",
            "PROV CONST DEL CALLAO", "CALLAO",
            "PROVINCIA CONSTITUCIONAL DEL CALLAO", "CALLAO",
            "LIMA METROPOLITANA", "LIMA");

    private NombreUbigeo() {
    }

    /**
     * Clave de comparación: sin tildes, en mayúsculas, sin signos y con un solo
     * espacio entre palabras. Por ejemplo " Nasca " y "NAZCA" dan "NAZCA";
     * "Prov. Const. del Callao" da "CALLAO". La Ñ se compara como N.
     */
    public static String clave(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String equivalente = EQUIVALENCIAS.get(limpio);
        if (equivalente != null) {
            return equivalente;
        }
        // NASCA dentro de un nombre más largo (por ejemplo "VISTA ALEGRE NASCA").
        return limpio.replaceAll("\\bNASCA\\b", "NAZCA");
    }

    /** Clave de un distrito completo: "DEPARTAMENTO|PROVINCIA|DISTRITO". */
    public static String clave(String departamento, String provincia, String distrito) {
        return clave(departamento) + "|" + clave(provincia) + "|" + clave(distrito);
    }
}
