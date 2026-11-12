package com.genlogs.app.sourcing.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Lo que el asistente confirma después de leer la URL (puede haber editado todo).
 * Flete, seguro y aduana van en soles. tipoCambio es obligatorio si la moneda no es PEN;
 * cuando entre el Paso 0, FE-D lo trae prellenado con el tipo de cambio del día.
 */
public record GuardarUrlRequest(
        Long idSolicitudItem,
        @NotBlank(message = "La URL es obligatoria") String url,
        @NotBlank(message = "El título es obligatorio") @Size(max = 500) String titulo,
        @Size(max = 100) String marca,
        @Size(max = 60) String numeroParte,
        @NotNull(message = "El precio es obligatorio") @Positive BigDecimal precio,
        @NotBlank(message = "La moneda es obligatoria") @Size(min = 3, max = 3) String codigoMoneda,
        String urlImagen,
        @NotBlank @Pattern(regexp = "NACIONAL|IMPORTADO", message = "El origen debe ser NACIONAL o IMPORTADO")
        String origen,
        @Positive BigDecimal tipoCambio,
        @PositiveOrZero BigDecimal flete,
        @PositiveOrZero BigDecimal seguro,
        @PositiveOrZero BigDecimal aduana,
        boolean editadoAMano) {
}
