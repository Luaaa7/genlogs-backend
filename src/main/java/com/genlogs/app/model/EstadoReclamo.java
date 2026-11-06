package com.genlogs.app.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Flujo de un reclamo/incidencia:
 * REGISTRADO -> EN_PROCESO -> RESUELTO -> CERRADO
 * (RESUELTO puede reabrirse a EN_PROCESO; REGISTRADO y EN_PROCESO pueden ANULARSE).
 * CERRADO y ANULADO son finales.
 */
public enum EstadoReclamo {
    REGISTRADO,
    EN_PROCESO,
    RESUELTO,
    CERRADO,
    ANULADO;

    public boolean esFinal() {
        return this == CERRADO || this == ANULADO;
    }

    public Set<EstadoReclamo> siguientesPermitidos() {
        return switch (this) {
            case REGISTRADO -> EnumSet.of(EN_PROCESO, ANULADO);
            case EN_PROCESO -> EnumSet.of(RESUELTO, ANULADO);
            case RESUELTO -> EnumSet.of(CERRADO, EN_PROCESO);
            case CERRADO, ANULADO -> EnumSet.noneOf(EstadoReclamo.class);
        };
    }

    public boolean puedePasarA(EstadoReclamo destino) {
        return siguientesPermitidos().contains(destino);
    }
}
