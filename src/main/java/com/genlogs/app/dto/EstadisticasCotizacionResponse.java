package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Espejo del shape inline que espera cotizacionesApi.obtenerEstadisticas()
 * en el frontend: { totalCotizaciones, totalPorEstado, montoTotalPendiente, tasaAprobacion }.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class EstadisticasCotizacionResponse {
    private long totalCotizaciones;
    private Map<String, Long> totalPorEstado;
    private BigDecimal montoTotalPendiente;
    private double tasaAprobacion;
}
