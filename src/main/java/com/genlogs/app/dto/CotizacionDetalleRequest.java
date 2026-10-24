package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Línea de cotización.
 *
 * El frontend (CotizacionDetalleForm.tsx) describe el ítem con texto libre
 * ("producto"/"servicio"/"descripcion") y no manda idUnidadMedida, por lo que
 * ambos formatos se aceptan: con producto/servicio del catálogo o como línea
 * descriptiva (descripcion_personalizada). La unidad de medida es opcional:
 * si no viene se usa la del producto o, en su defecto, la unidad activa por
 * defecto (ver CotizacionService.construirLinea).
 */
@Getter
@Setter
public class CotizacionDetalleRequest {

    private Long idProducto;
    private Long idServicio;

    /** Opcional: si no viene se deduce (producto) o se usa la unidad por defecto. */
    private Integer idUnidadMedida;

    private String descripcionPersonalizada;

    /** Alias del frontend: descripción libre de la línea. */
    private String descripcion;

    /** Texto libre del frontend: se toma como descripción si no hay idProducto. */
    private String producto;

    /** Texto libre del frontend: se toma como descripción si no hay idServicio. */
    private String servicio;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    private BigDecimal precioUnitario;

    @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo")
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;
}
