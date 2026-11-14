package com.genlogs.app.sourcing.extraccion;

import java.math.BigDecimal;

/**
 * Precio y moneda de una ficha, ya normalizados (A4, v3.3).
 *
 * @param monto        precio con 2 decimales; null si la ficha no trae precio (vacío, nunca 0)
 * @param codigoMoneda ISO de 3 letras (USD, PEN, EUR...); null si no se pudo saber
 */
public record PrecioExtraido(BigDecimal monto, String codigoMoneda) {

    public static final PrecioExtraido VACIO = new PrecioExtraido(null, null);

    public boolean tienePrecio() {
        return monto != null;
    }
}