package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** payment_id que Mercado Pago agrega a la URL de regreso. */
public record ConfirmarPagoRequest(
        @NotBlank(message = "Falta el número de pago")
        @Pattern(regexp = "\\d{1,30}", message = "El número de pago no es válido")
        String idPago) {
}