package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cotización ya guardada y el seguimiento que dejó el registro de la respuesta. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaClienteRegistradaResponse {

    private CotizacionResponse cotizacion;
    private SeguimientoCotizacionResponse seguimiento;
}
