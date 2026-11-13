package com.genlogs.app.fuentes.config;

/**
 * Resultado de "Probar sesión" (L1). Igual que el CHECK de fuente_busqueda.estado.
 * No es lo mismo que activa: una fuente puede tener la sesión bien y estar apagada.
 */
public enum EstadoFuente {
    SIN_PROBAR,
    ACTIVA,
    CON_ERROR,
    REQUIERE_VERIFICACION
}
