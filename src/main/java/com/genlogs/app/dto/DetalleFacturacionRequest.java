package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleFacturacionRequest {

    // Exactamente uno de los dos: idProducto o idServicio
    private Long idProducto;

    private Long idServicio;

    private String descripcionPersonalizada;

    @NotNull(message = "Debe indicar la cantidad")
    private BigDecimal cantidad;

    @NotNull(message = "Debe indicar el precio unitario")
    private BigDecimal precioUnitario;

    private BigDecimal descuentoUnitario;
}
