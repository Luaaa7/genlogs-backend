package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lo que ve el cliente en el portal. Sin margen, costo, precio de proveedor ni datos internos. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortalCotizacionResponse {

    private Long id;
    private String codigo;
    private LocalDate fechaCotizacion;
    private LocalDate fechaValidez;
    private String estado;
    private BigDecimal total;
    private String moneda;
    private List<Linea> lineas;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Linea {
        private String descripcion;
        private BigDecimal cantidad;
        private String unidad;
        private BigDecimal precioUnitario;
        private BigDecimal importe;
    }
}
