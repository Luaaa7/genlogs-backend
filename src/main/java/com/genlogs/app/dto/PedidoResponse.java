package com.genlogs.app.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Pedido del portal. No incluye precios ni datos de otros clientes. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PedidoResponse {

    private Long id;
    private String codigo;
    private String fecha;
    private String estado;
    private String asunto;
    private String descripcion;
    private List<PedidoItemResponse> items;
}
