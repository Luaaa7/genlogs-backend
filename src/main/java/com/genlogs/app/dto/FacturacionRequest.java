package com.genlogs.app.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturacionRequest {

    @NotNull(message = "Debe indicar la orden de compra")
    private Long idOrdenCompra;

    @NotNull(message = "Debe indicar el tipo de comprobante")
    private Integer idTipoComprobante;

    @NotNull(message = "Debe indicar la condición de pago")
    private Integer idCondicionPago;

    @NotNull(message = "Debe indicar la moneda")
    private Integer idMoneda;

    @NotBlank(message = "Debe indicar la serie del comprobante")
    private String serieComprobante;

    @NotBlank(message = "Debe indicar el número del comprobante")
    private String numeroComprobante;

    private LocalDate fechaEmision;

    private LocalDate fechaVencimiento;

    @NotEmpty(message = "La facturación debe tener al menos una línea")
    @Valid
    private List<DetalleFacturacionRequest> lineas;
}
