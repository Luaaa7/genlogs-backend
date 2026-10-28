package com.genlogs.app.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de POST /api/facturacion/emitir: igual que registrar una factura,
 * pero sin serie ni número (los pone el sistema, como hace SUNAT).
 */
@Getter
@Setter
public class EmitirFacturaRequest {

    @NotNull(message = "Debe indicar la orden de compra")
    private Long idOrdenCompra;

    @NotNull(message = "Debe indicar el tipo de comprobante")
    private Integer idTipoComprobante;

    @NotNull(message = "Debe indicar la condición de pago")
    private Integer idCondicionPago;

    @NotNull(message = "Debe indicar la moneda")
    private Integer idMoneda;

    private LocalDate fechaEmision;

    private LocalDate fechaVencimiento;

    @NotEmpty(message = "El comprobante debe tener al menos una línea")
    @Valid
    private List<DetalleFacturacionRequest> lineas;
}