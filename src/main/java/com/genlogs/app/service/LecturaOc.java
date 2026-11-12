package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo que se pudo leer del PDF de la minera. Cualquier dato puede faltar. */
public record LecturaOc(String numeroOc, LocalDate fechaEmision, BigDecimal monto, String moneda) {

    public static LecturaOc vacia() {
        return new LecturaOc(null, null, null, null);
    }

    public boolean hayAlgo() {
        return numeroOc != null || fechaEmision != null || monto != null;
    }
}
