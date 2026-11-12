package com.genlogs.app.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Pedido del portal. No incluye precios, datos de otros clientes ni nada interno (plazo, pausa, responsable). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PedidoResponse {

    private Long id;
    private String codigo;
    private String fecha;
    /** Estado interno tal cual (el frontend ya lo traduce). */
    private String estado;
    /**
     * Etapa de la barra del cliente (D-14): RECIBIDA, EN_BUSQUEDA, EN_ESPERA_PROVEEDOR, COTIZADA,
     * RECHAZADA o CANCELADA. Sin plazos ni fechas internas.
     */
    private String etapa;
    /** true mientras el cliente todavía puede cancelarlo (PENDIENTE o EN_REVISION). */
    private Boolean puedeCancelar;
    private String asunto;
    private String descripcion;
    private List<PedidoItemResponse> items;
}
