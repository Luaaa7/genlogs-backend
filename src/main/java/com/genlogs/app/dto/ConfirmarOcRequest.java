package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Corrección de lo leído en el PDF de la minera. */
@Getter
@Setter
public class ConfirmarOcRequest {

    @NotBlank(message = "Indica el número de la OC del cliente")
    @Size(max = 40, message = "El número de la OC del cliente admite hasta 40 caracteres")
    private String numeroOc;

    @NotNull(message = "Indica el monto de la OC del cliente")
    @Positive(message = "El monto de la OC del cliente debe ser mayor que cero")
    @Digits(integer = 12, fraction = 2, message = "El monto admite hasta 2 decimales")
    private BigDecimal monto;

    private LocalDate fechaEmision;

    @Pattern(regexp = "^[A-Za-z]{3}$", message = "La moneda de la OC debe ser un código de 3 letras, como PEN o USD")
    private String moneda;
}
