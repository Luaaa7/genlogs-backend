package com.genlogs.app.sourcing;

/**
 * Ciclo de vida de una referencia: CAPTURADO → ÚTIL / DESCARTADO → CONVERTIDO.
 * Valores del CHECK de historial_sourcing.estado (sin tilde en la base).
 */
public enum EstadoSourcing {
    CAPTURADO, UTIL, DESCARTADO, CONVERTIDO;

    /** El asistente solo puede marcar ÚTIL o DESCARTADO; CONVERTIDO lo pone la vitrina (Fase 3). */
    public boolean puedeMarcarseManualmente() {
        return this == UTIL || this == DESCARTADO;
    }
}
