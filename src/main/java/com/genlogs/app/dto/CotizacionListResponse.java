package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CotizacionListResponse {
    private Long idCotizacion;
    private String codigoCotizacion;
    private LocalDate fechaCotizacion;
    private String clienteRazonSocial;
    private String estado;
    private String moneda;
    private BigDecimal total;
}