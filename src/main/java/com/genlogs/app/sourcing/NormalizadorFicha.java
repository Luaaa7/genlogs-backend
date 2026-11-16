package com.genlogs.app.sourcing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.genlogs.app.fuentes.config.FichaFuente;

/**
 * Normaliza las fichas del lector (BPMN 9-B.1 tarea A): precio vacío se queda vacío (nunca 0),
 * moneda en ISO de 3 letras, sin duplicar el enlace, máximo 8.
 */
final class NormalizadorFicha {

    static final int MAX_FICHAS = 8;

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
            String clave = enlace != null ? enlace : nombre + "|" + texto(f.precio());
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

    private static String texto(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
