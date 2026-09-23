package com.genlogs.app.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrdenCompraResponse {
    private Long idOrdenCompra;
    private String numeroOrdenCompra;
    private String clienteRazonSocial;
    private LocalDate fechaEmisionCliente;
    private LocalDate fechaRecepcion;
    private String estado;
}
