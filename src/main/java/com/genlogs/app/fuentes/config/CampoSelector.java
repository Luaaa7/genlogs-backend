package com.genlogs.app.fuentes.config;

/**
 * Los campos que extrae cada lector (RF-94). ITEM es el bloque de cada resultado en la
 * página de búsqueda (el contenedor); los otros 6 se leen dentro de ese bloque.
 * ITEM ya está en el CHECK de fuente_selector (v3_3.sql de Karla).
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