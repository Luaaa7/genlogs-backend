package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjuntoCotizacionResponse {
    private Long idAdjunto;
    private String nombreArchivo;
    private String urlArchivo;
}