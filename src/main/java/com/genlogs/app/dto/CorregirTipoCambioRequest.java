package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Corrección manual del tipo de cambio del día (PUT /api/tipo-cambio, Jefe Adm.).
 * Fuera de 1–10 casi seguro es un error de tipeo (p. ej. 3751 en vez de 3,751).
 */
public record CorregirTipoCambioRequest(
        @NotNull(message = "Escribe el tipo de cambio")
        @DecimalMin(value = "1", message = "El tipo de cambio debe estar entre 1 y 10")
        @DecimalMax(value = "10", message = "El tipo de cambio debe estar entre 1 y 10")
        @Digits(integer = 2, fraction = 4, message = "Usa hasta 4 decimales")
        BigDecimal valor) {
}
