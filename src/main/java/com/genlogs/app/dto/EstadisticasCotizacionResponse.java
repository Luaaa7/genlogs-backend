package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Espejo del shape inline que espera cotizacionesApi.obtenerEstadisticas()
 * en el frontend: { totalCotizaciones, totalPorEstado, montoTotalPendiente, montoPendientePorMoneda, tasaAprobacion }.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class EstadisticasCotizacionResponse {
    private long totalCotizaciones;
    private Map<String, Long> totalPorEstado;
    /** Suma de todas las monedas juntas. Se mantiene por compatibilidad; no usar para mostrar. */
    private BigDecimal montoTotalPendiente;
    /** Monto pendiente por moneda, p. ej. { "USD": 98000.00, "PEN": 27954.38 }. */
    private Map<String, BigDecimal> montoPendientePorMoneda;
    private double tasaAprobacion;
}