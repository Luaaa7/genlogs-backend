package com.genlogs.app.dto;

import java.math.BigDecimal;

/** Meta del periodo y cuántas cotizaciones de ese mes entraron en el plazo. */
public record MetaPeriodoResponse(
        int anio,
        int mes,
        BigDecimal metaPorcentaje,
        int cotizacionesEnPlazo,
        int cotizacionesConsideradas,
        BigDecimal porcentaje,
        Boolean cumple) {
}
