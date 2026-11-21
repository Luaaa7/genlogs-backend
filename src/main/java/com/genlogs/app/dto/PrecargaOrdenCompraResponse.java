package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Datos de la cotización listos para armar la orden de compra.
 * Los montos van en soles (PEN). La orden se vincula con {@code idCotizacion}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrecargaOrdenCompraResponse {

    private Long idCotizacion;
    private String codigoCotizacion;
    private Long idCliente;
    private String cliente;
    /** Siempre PEN. */
    private String moneda;
    /** Moneda original de la cotización (PEN o USD). */
    private String monedaCotizacion;
    /** Soles por 1 USD. Nulo si la cotización ya está en soles. */
    private BigDecimal tipoCambio;
    private Integer idCondicionPago;
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;
    private List<Item> items;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long idCotizacionDetalle;
        private String descripcion;
        private BigDecimal cantidad;
        private String unidad;
        private BigDecimal precioUnitario;
        private BigDecimal importe;
    }
}
