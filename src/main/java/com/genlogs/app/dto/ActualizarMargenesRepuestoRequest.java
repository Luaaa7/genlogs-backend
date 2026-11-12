package com.genlogs.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de los tres márgenes de repuestos (D-17) en una sola operación:
 * se guardan los tres o ninguno. La regla MIN <= DEFAULT <= MAX la valida el servicio.
 */
public record ActualizarMargenesRepuestoRequest(
        @NotNull(message = "El margen mínimo es obligatorio")
        @DecimalMin(value = "0", message = "El margen mínimo no puede ser negativo")
        @Digits(integer = 5, fraction = 2, message = "El margen mínimo admite hasta 2 decimales")
        BigDecimal min,

        @NotNull(message = "El margen por defecto es obligatorio")
        @DecimalMin(value = "0", message = "El margen por defecto no puede ser negativo")
        @Digits(integer = 5, fraction = 2, message = "El margen por defecto admite hasta 2 decimales")
        BigDecimal porDefecto,

        @NotNull(message = "El margen máximo es obligatorio")
        @DecimalMin(value = "0", message = "El margen máximo no puede ser negativo")
        @Digits(integer = 5, fraction = 2, message = "El margen máximo admite hasta 2 decimales")
        BigDecimal max) {
}
