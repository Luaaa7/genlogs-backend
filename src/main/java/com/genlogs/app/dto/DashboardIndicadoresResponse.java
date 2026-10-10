package com.genlogs.app.dto;

import java.math.BigDecimal;

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
    private long clientesActivos;
    private long ordenesCompraProceso;
    private double porcentajeVariacionFacturacion;
}
