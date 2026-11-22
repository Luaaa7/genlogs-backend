package com.genlogs.app.fuentes.config;

/**
 * Los campos que extrae cada lector (RF-94). ITEM es el bloque de cada resultado
 * en la página de búsqueda (A4); los otros 6 se leen dentro de ese bloque o en la ficha.
 * USUARIO, CLAVE, ENVIAR y EXITO son de la página LOGIN: guardan dónde está la casilla
 * del correo, la de la clave, el botón para ingresar y algo que solo aparece ya dentro.
 * CONTINUAR (D-42) es opcional: el botón que, en un ingreso de dos pantallas, pasa de la
 * casilla del usuario a la de la clave.
 * Nunca guardan la clave en sí: esa sigue cifrada en fuente_busqueda.
 * Mismo orden que el CHECK ck_fuente_selector_campo de v3_3.sql.
 */
public enum CampoSelector {
    ITEM,
    NOMBRE,
    DESCRIPCION,
    PRECIO,
    MONEDA,
    IMAGEN,
    ENLACE,
    USUARIO,
    CLAVE,
    ENVIAR,
    EXITO,
    CONTINUAR
}
