package com.genlogs.app.fuentes.config;

import java.util.List;

/**
 * Respuesta del servicio de lectura al consultar una tienda (P1/P2). La sesión no viaja aquí.
 */
public record ResultadoLectura(Tipo tipo, List<FichaFuente> fichas, String mensaje, boolean reintentable) {

    public enum Tipo { OK, BLOQUEADA, VERIFICACION, ERROR, SIN_RESULTADOS, NO_CONECTADO }

    public static ResultadoLectura ok(List<FichaFuente> fichas) {
        if (fichas == null || fichas.isEmpty()) {
            return sinResultados();
        }
        return new ResultadoLectura(Tipo.OK, List.copyOf(fichas), null, false);
    }

    public static ResultadoLectura sinResultados() {
        return new ResultadoLectura(Tipo.SIN_RESULTADOS, List.of(), null, false);
    }

    public static ResultadoLectura bloqueada(String mensaje) {
        return new ResultadoLectura(Tipo.BLOQUEADA, List.of(), mensaje, false);
    }

    public static ResultadoLectura verificacion(String mensaje) {
        return new ResultadoLectura(Tipo.VERIFICACION, List.of(), mensaje, false);
    }

    public static ResultadoLectura error(String mensaje, boolean reintentable) {
        return new ResultadoLectura(Tipo.ERROR, List.of(), mensaje, reintentable);
    }

    /** El servicio de lectura (P2) no está cableado. No es una caída de la tienda. */
    public static ResultadoLectura noConectado(String mensaje) {
        return new ResultadoLectura(Tipo.NO_CONECTADO, List.of(), mensaje, false);
    }

    @Override
    public String toString() {
        int n = fichas == null ? 0 : fichas.size();
        return "ResultadoLectura[tipo=" + tipo + ", fichas=" + n + "]";
    }
}
