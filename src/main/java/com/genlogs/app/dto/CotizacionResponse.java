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
    private BigDecimal total;
    private String moneda;
}
