package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/** Comprobante en el portal del cliente (BPMN 4.1). Sin datos internos. */
@Getter
@Builder
public class PortalComprobanteResponse {
    private Long id;
    /** Serie-número, p. ej. F001-00000012. */
    private String comprobante;
    private String tipo;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal total;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    /** Código ISO: PEN o USD. */
    private String moneda;
    private String estado;
    private String codigoEstado;
    private String numeroOcCliente;
    /** true si se puede pagar con Mercado Pago ahora mismo. */
    private boolean puedePagarEnLinea;
    /** Por qué no se puede pagar en línea (null si sí se puede). */
    private String motivoSinPago;
}