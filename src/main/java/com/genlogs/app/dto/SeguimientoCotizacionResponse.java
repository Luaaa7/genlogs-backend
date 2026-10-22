package com.genlogs.app.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Evento de seguimiento de una cotización.
 *
 * Mantiene estado/usuario/fechaEvento/comentario y suma los alias que
 * consume el frontend (SeguimientoCotizacion en src/types/cotizacion.types.ts):
 * estadoCodigo/estadoNuevo, usuarioNombre, fecha y observaciones.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeguimientoCotizacionResponse {
    private Long idSeguimiento;
    /** Alias de idSeguimiento. */
    private Long id;
    private Integer idEstadoCotizacion;
    /** Nombre legible del estado. */
    private String estado;
    /** Código del estado (p. ej. "APROBADA"). */
    private String estadoCodigo;
    /** Alias de estadoCodigo: el estado al que se cambió. */
    private String estadoNuevo;
    /**
     * Estado anterior al cambio (p. ej. "BORRADOR"). El frontend lo pinta
     * como "Borrador → Aprobada"; es null en el primer seguimiento.
     */
    private String estadoAnterior;
    private String usuario;
    /** Alias de usuario. */
    private String usuarioNombre;
    private LocalDateTime fechaEvento;
    /** Alias de fechaEvento. */
    private LocalDateTime fecha;
    private String comentario;
    /** Alias de comentario. */
    private String observaciones;
}
