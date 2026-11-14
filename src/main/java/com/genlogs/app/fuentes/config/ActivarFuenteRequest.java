package com.genlogs.app.fuentes.config;

import jakarta.validation.constraints.NotNull;

public record ActivarFuenteRequest(@NotNull(message = "Indica si la fuente queda activa") Boolean activa) {
}
