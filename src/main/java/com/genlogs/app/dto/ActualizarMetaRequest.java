package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record ActualizarMetaRequest(
        @NotNull(message = "Indica la meta")
        @DecimalMin(value = "0.0", message = "La meta tiene que estar entre 0 y 100")
        @DecimalMax(value = "100.0", message = "La meta tiene que estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "La meta tiene como máximo 2 decimales")
        BigDecimal metaPorcentaje) {
}
