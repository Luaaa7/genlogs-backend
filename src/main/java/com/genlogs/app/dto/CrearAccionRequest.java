package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearAccionRequest(
        @NotBlank(message = "La descripción de la acción no puede ir vacía") String descripcion) {
}
