package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * RF-75 · Costo puesto en Lima, en soles:
 * precio × tipo de cambio + flete + seguro + aduana.
 * Flete, seguro y aduana se escriben en soles; si el precio ya está en soles,
 * el tipo de cambio es 1.
 */
public final class CostoPuestoLima {

    private CostoPuestoLima() {
    }

    public static BigDecimal calcular(BigDecimal precio, String codigoMoneda, BigDecimal tipoCambio,
                                      BigDecimal flete, BigDecimal seguro, BigDecimal aduana) {
        if (precio == null || precio.signum() <= 0) {
            throw new BusinessException("El precio debe ser mayor a cero");
        }
        BigDecimal tc = BigDecimal.ONE;
        if (!esSoles(codigoMoneda)) {
            if (tipoCambio == null || tipoCambio.signum() <= 0) {
                throw new BusinessException("Indica el tipo de cambio para convertir " + codigoMoneda + " a soles");
            }
            tc = tipoCambio;
        }
        BigDecimal total = precio.multiply(tc)
                .add(noNegativo(flete, "flete"))
                .add(noNegativo(seguro, "seguro"))
                .add(noNegativo(aduana, "aduana"));
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean esSoles(String codigoMoneda) {
        return codigoMoneda == null || codigoMoneda.equalsIgnoreCase("PEN");
    }

    private static BigDecimal noNegativo(BigDecimal v, String nombre) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw new BusinessException("El " + nombre + " no puede ser negativo");
        return v;
    }
}
