package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/** DTO para asociar un proveedor a un producto, con su precio referencial. */
@Getter
@Setter
public class ProductoProveedorRequest {

    @NotNull(message = "Debe indicar el proveedor")
    private Long idProveedor;

    @PositiveOrZero(message = "El precio referencial no puede ser negativo")
    private BigDecimal precioReferencial;

    /** Obligatorio si se informa precioReferencial (regla ck_producto_proveedor_moneda de la BD). */
    private Integer idMoneda;

    @PositiveOrZero(message = "El tiempo de entrega no puede ser negativo")
    private Integer tiempoEntregaDias;

    private Boolean proveedorPreferido = false;
}
