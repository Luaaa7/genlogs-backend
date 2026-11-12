package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarParametroRequest(
        @NotBlank(message = "El valor del parámetro no puede estar vacío")
        @Size(max = 100, message = "El valor del parámetro no puede pasar de 100 caracteres")
        String valor) {
}
