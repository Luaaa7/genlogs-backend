package com.genlogs.app.dto.gestion;

import jakarta.validation.constraints.NotNull;

public record ReasignarSolicitudRequest(
        @NotNull(message = "Elige a quién se asigna")
        Long idUsuario) {
}
