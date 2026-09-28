package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoCotizacionResponse {
    private Integer idEstadoCotizacion;
    private String codigoEstado;
    private String nombreEstado;
    private Integer ordenFlujo;
    private Boolean esFinal;
}