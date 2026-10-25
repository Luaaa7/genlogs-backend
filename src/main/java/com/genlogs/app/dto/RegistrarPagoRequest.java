package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrarPagoRequest {

    @NotNull(message = "Debe indicar el monto pagado")
    @Positive(message = "El monto pagado debe ser mayor a cero")
    private BigDecimal montoPagado;

    @NotNull(message = "La fecha de pago es obligatoria")
    private LocalDate fechaPago;

    @NotBlank(message = "El medio de pago es obligatorio")
    private String medioPago;

    private String numeroOperacion;
}
