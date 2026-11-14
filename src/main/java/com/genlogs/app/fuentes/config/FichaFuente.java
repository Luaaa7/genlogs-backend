package com.genlogs.app.fuentes.config;

/**
 * Los 6 campos que extrae el lector (A4 / BPMN 9-B.1 tarea A). El precio llega en texto
 * para no convertirlo a cero cuando la tienda no lo publica. El stock no forma parte del contrato.
 */
public record FichaFuente(
        String nombre,
        String descripcion,
        String precio,
        String moneda,
        String imagen,
        String enlace) {
}
