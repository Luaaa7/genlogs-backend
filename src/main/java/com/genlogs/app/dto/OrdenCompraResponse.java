package com.genlogs.app.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrdenCompraResponse {
    private Long idOrdenCompra;
    private Long idCotizacion;
    private String codigoCotizacion;
    private String numeroOrdenCompra;
    private String clienteRazonSocial;
    private String cliente;
    private String estadoCodigo;
    private LocalDate fechaEmisionCliente;
    private LocalDate fechaRecepcion;
    private String estado;
    private String urlArchivo;
    private String observaciones;
}
