package com.genlogs.app.dto;

import java.time.LocalDateTime;

public record AccionCorrectivaResponse(
        Long id,
        int anio,
        int mes,
        String descripcion,
        String estado,
        LocalDateTime fecha) {
}
