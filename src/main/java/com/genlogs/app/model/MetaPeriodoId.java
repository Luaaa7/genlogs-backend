package com.genlogs.app.model;

import java.io.Serializable;
import java.util.Objects;

/** Año y mes de una meta. El par es único. */
public class MetaPeriodoId implements Serializable {

    private Short anio;
    private Short mes;

    public MetaPeriodoId() {
    }

    public MetaPeriodoId(Short anio, Short mes) {
        this.anio = anio;
        this.mes = mes;
    }

    public Short getAnio() {
        return anio;
    }

    public Short getMes() {
        return mes;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof MetaPeriodoId id)) {
            return false;
        }
        return Objects.equals(anio, id.anio) && Objects.equals(mes, id.mes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anio, mes);
    }
}
