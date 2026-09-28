package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class EstadoOrdenCompraResponse {
    private Integer idEstadoOrdenCompra;
    private String codigoEstado;
    private String nombreEstado;
    private Boolean esFinal;
}
