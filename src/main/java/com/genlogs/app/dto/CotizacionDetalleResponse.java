package com.genlogs.app.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CotizacionDetalleResponse {
    private Long idCotizacionDetalle;
    private Long idProducto;
    private String productoNombre;
    private Long idServicio;
    private String servicioNombre;
    private String unidadMedida;
    private String descripcionPersonalizada;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal descuentoUnitario;
    private BigDecimal importeLinea;
}