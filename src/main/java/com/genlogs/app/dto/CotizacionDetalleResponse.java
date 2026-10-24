package com.genlogs.app.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Línea de cotización en la respuesta.
 *
 * Mantiene los campos originales y suma los alias que consume el frontend
 * (CotizacionDetalle en src/types/cotizacion.types.ts): id, idUnidadMedida,
 * producto, servicio, descripcion y subtotal.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CotizacionDetalleResponse {
    private Long idCotizacionDetalle;
    /** Alias de idCotizacionDetalle. */
    private Long id;
    private Long idProducto;
    private String productoNombre;
    /** Nombre del producto (alias de productoNombre). */
    private String producto;
    private Long idServicio;
    private String servicioNombre;
    /** Nombre del servicio (alias de servicioNombre). */
    private String servicio;
    private Integer idUnidadMedida;
    private String unidadMedida;
    private String descripcionPersonalizada;
    /** Alias de descripcionPersonalizada. */
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal descuentoUnitario;
    private BigDecimal importeLinea;
    /** Alias de importeLinea. */
    private BigDecimal subtotal;
}
