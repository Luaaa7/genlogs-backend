package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CotizacionDetalleRequest {

    private Long idProducto;
    private Long idServicio;

    @NotNull(message = "Debe indicar la unidad de medida")
    private Integer idUnidadMedida;

    private String descripcionPersonalizada;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    private BigDecimal precioUnitario;

    @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo")
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;
}