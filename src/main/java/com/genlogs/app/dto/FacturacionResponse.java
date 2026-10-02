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
    private Long idOrdenCompra;
    private String numeroOrdenCompra;
    private String numeroComprobante;
    private String codigoComprobante;
    private String cliente;
    private String estadoCodigo;
    private String tipoComprobante;
    private String clienteRazonSocial;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal total;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private String moneda;
    private String estado;
}
