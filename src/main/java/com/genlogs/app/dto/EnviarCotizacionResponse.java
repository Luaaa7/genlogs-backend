package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Espejo de { success, mensaje } que espera cotizacionesApi.enviarCotizacion() en el frontend. */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnviarCotizacionResponse {
    private boolean success;
    private String mensaje;
}
