package com.genlogs.app.fuentes.config;

/**
 * Los campos que extrae cada lector (RF-94). ITEM es el bloque de cada resultado
 * en la página de búsqueda (A4); los otros 6 se leen dentro de ese bloque o en la ficha.
 * Mismo orden que el CHECK ck_fuente_selector_campo de v3_3.sql.
 */
public enum CampoSelector {
    ITEM,
    NOMBRE,
    DESCRIPCION,
    PRECIO,
    MONEDA,
    IMAGEN,
    ENLACE
}
