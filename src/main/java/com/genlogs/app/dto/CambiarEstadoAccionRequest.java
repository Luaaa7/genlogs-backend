package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CambiarEstadoAccionRequest(
        @NotBlank(message = "Indica el estado") String estado) {
}
