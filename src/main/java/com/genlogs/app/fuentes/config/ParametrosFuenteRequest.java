package com.genlogs.app.fuentes.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Pausa entre consultas y límite diario de una tienda (D-21: no saturar ni ser bloqueados). */
public record ParametrosFuenteRequest(
        @NotNull(message = "Indica la pausa en segundos")
        @Min(value = 0, message = "La pausa no puede ser negativa")
        @Max(value = 300, message = "La pausa no puede pasar de 300 segundos")
        Integer pausaSegundos,
        @NotNull(message = "Indica el límite diario")
        @Min(value = 1, message = "El límite diario debe ser al menos 1")
        @Max(value = 1000, message = "El límite diario no puede pasar de 1000")
        Integer limiteDiario) {
}
