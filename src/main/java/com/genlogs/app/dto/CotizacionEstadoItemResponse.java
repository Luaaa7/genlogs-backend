package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contrato exacto esperado por el frontend en
 * src/types/dashboard.types.ts -> CotizacionEstadoItem.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionEstadoItemResponse {
    private String estado;
    private long cantidad;
    private double porcentaje;
}
