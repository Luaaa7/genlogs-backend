package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrarPagoRequest {

    @NotNull(message = "Debe indicar el monto pagado")
    @Positive(message = "El monto pagado debe ser mayor a cero")
    @Digits(integer = 14, fraction = 2, message = "El monto pagado admite como máximo 2 decimales")
    private BigDecimal montoPagado;

    @NotNull(message = "Debe indicar la fecha del pago")
    @PastOrPresent(message = "La fecha del pago no puede ser futura")
    private LocalDate fechaPago;

    @NotBlank(message = "Debe indicar el medio de pago")
    @Size(max = 40, message = "El medio de pago no puede superar 40 caracteres")
    private String medioPago;

    /** Opcional: número de operación o de voucher. */
    @Size(max = 40, message = "El número de operación no puede superar 40 caracteres")
    private String numeroOperacion;
}
