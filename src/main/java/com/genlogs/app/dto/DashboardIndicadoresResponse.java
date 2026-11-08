package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contrato exacto esperado por el frontend en
 * src/types/dashboard.types.ts -> DashboardIndicadoresResponse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardIndicadoresResponse {
    private BigDecimal totalFacturado;
    private long cotizacionesPendientes;
    /**
     * Monto de las cotizaciones pendientes por moneda, sin mezclar monedas:
     * { "S/": 27954.38, "$": 98000.00 }. Los euros no se incluyen (el
     * frontend no los usa).
     */
    private Map<String, BigDecimal> montoCotizacionesPendientes;
    /**
     * Días promedio entre el envío de una cotización y la primera respuesta
     * del cliente. null si todavía ninguna cotización enviada fue respondida.
     */
    private Double tiempoRespuestaPromedioDias;
    private long clientesActivos;
    private long ordenesCompraProceso;
    private double porcentajeVariacionFacturacion;
}
