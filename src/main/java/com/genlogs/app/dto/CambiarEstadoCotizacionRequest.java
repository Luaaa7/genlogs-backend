package com.genlogs.app.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de PATCH /api/cotizaciones/{id}/estado.
 *
 * El frontend (CotizacionDetallePage.tsx) manda "estadoNuevo" y
 * "observaciones"; el backend aceptaba "codigoEstado" y "comentario". Se
 * aceptan ambos nombres y la resolución/validación se hace en
 * CotizacionService.cambiarEstado.
 */
@Getter
@Setter
public class CambiarEstadoCotizacionRequest {

    /** Código del nuevo estado. Alias aceptado: {@code estadoNuevo}. */
    private String codigoEstado;
    private String estadoNuevo;

    /** Comentario del cambio. Alias aceptado: {@code observaciones}. */
    private String comentario;
    private String observaciones;

    /** Devuelve el código de estado venga con el nombre canónico o con el alias. */
    public String codigoEstadoResuelto() {
        return codigoEstado != null && !codigoEstado.isBlank() ? codigoEstado.trim() : estadoNuevo;
    }

    /** Devuelve el comentario venga con el nombre canónico o con el alias. */
    public String comentarioResuelto() {
        return comentario != null && !comentario.isBlank() ? comentario : observaciones;
    }
}
