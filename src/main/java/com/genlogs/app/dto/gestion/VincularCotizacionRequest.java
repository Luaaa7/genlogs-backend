package com.genlogs.app.dto.gestion;

import jakarta.validation.constraints.NotNull;

/** Cotización que se acaba de crear desde la solicitud. */
public record VincularCotizacionRequest(@NotNull Long idCotizacion) {
}
