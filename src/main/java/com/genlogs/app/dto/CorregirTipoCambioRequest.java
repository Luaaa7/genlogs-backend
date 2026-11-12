package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record CorregirTipoCambioRequest(
        @NotNull(message = "Indica el tipo de cambio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El tipo de cambio tiene que ser mayor que 0")
        @Digits(integer = 6, fraction = 4, message = "El tipo de cambio admite hasta 4 decimales")
        BigDecimal valor) {
}
