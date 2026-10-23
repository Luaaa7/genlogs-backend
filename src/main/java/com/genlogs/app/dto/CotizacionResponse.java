package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CotizacionResponse {
    private Long idCotizacion;
    private String codigoCotizacion;
    private String clienteRazonSocial;
    private LocalDate fechaCotizacion;
    private LocalDate fechaValidez;
    private String estado;
    // Calculados desde vw_cotizacion_totales (la tabla no guarda el total)
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;
    private String moneda;
    /** Codigo ISO (PEN/USD/EUR); "moneda" lleva el simbolo. */
    private String monedaCodigo;
    private String condicionPago;
    private Long idCliente;
    private String clienteEmail;
    private String observaciones;
}
