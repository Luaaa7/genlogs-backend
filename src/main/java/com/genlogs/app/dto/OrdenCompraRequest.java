package com.genlogs.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdenCompraRequest {

    @NotNull(message = "Debe indicar la cotización")
    private Long idCotizacion;

    private Integer idCondicionPago;

    @NotBlank(message = "Debe indicar el número de orden de compra")
    private String numeroOrdenCompra;

    private LocalDate fechaEmisionCliente;

    private String observaciones;
}
