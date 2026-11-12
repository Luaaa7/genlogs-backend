package com.genlogs.app.sourcing.dto;

import com.genlogs.app.sourcing.EstadoSourcing;
import jakarta.validation.constraints.NotNull;

/** Botón ÚTIL / DESCARTADO del panel de búsqueda. */
public record CambiarEstadoReferenciaRequest(
        @NotNull(message = "El estado es obligatorio") EstadoSourcing estado) {
}
