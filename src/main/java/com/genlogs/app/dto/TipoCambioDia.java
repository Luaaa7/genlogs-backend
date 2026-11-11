package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.genlogs.app.enums.FuenteTipoCambio;

/**
 * Tipo de cambio que se debe usar hoy (Paso 0).
 *
 * @param fecha        dia al que corresponde el valor
 * @param codigoMoneda moneda extranjera (USD)
 * @param valor        soles por 1 unidad de la moneda (valor de venta, D-19)
 * @param fuente       SUNAT, SBS o MANUAL
 * @param esDeHoy      false si hoy todavia no hay valor y se usa el ultimo guardado
 */
public record TipoCambioDia(
        LocalDate fecha,
        String codigoMoneda,
        BigDecimal valor,
        FuenteTipoCambio fuente,
        boolean esDeHoy) {
}