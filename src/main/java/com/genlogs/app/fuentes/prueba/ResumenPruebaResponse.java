package com.genlogs.app.fuentes.prueba;

import java.time.LocalDateTime;
import java.util.List;

/** Tabla de aciertos sobre 10 por tienda (M3, RF-94): la última corrida de cada una. */
public record ResumenPruebaResponse(
        String codigo,
        String nombre,
        boolean activa,
        String estado,
        Short aciertos,
        int corrida,
        boolean aprobada,
        List<Detalle> detalle) {

    public record Detalle(String pedido, boolean acerto, String enlace, LocalDateTime probadoEn) {
    }
}