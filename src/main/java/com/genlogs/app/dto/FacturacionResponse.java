package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class FacturacionResponse {
    private Long idFacturacion;
    private String numeroComprobante;
    private String clienteRazonSocial;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal montoPagado;
    private String estado;
}
