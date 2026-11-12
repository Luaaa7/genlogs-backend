package com.genlogs.app.dto.gestion;

import com.genlogs.app.enums.TipoPlazo;

import jakarta.validation.constraints.NotNull;

public record ReclasificarSolicitudRequest(
        @NotNull(message = "Elige el tipo de plazo")
        TipoPlazo tipoPlazo) {
}
