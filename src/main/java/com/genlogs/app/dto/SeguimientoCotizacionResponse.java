
package com.genlogs.app.dto;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Evento de seguimiento de una cotización.
 *
 * Mantiene los campos originales de main y agrega alias
 * compatibles con el frontend.
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

    /** Código del estado, por ejemplo APROBADA. */
    private String codigoEstado;

    /** Alias del código del estado para el frontend. */
    private String estadoCodigo;

    /** Estado nuevo después del cambio. */
    private String estadoNuevo;

    /** Estado anterior al cambio. */
    private String estadoAnterior;

    private String usuario;

    /** Alias de usuario. */
    private String usuarioNombre;

    /**
     * Instante absoluto del evento.
     * Se serializa con zona horaria para mostrar la hora local
     * de cada navegador.
     */
    private Instant fechaEvento;

    /** Alias de fechaEvento. */
    private Instant fecha;

    private String comentario;

    /** Alias de comentario. */
    private String observaciones;

    /** Día en que respondió el cliente. Nulo si el evento no es una respuesta. */
    private LocalDate fechaRespuesta;

    /** correo, whatsapp, llamada u otro. */
    private String medio;

    /** Ruta o URL del adjunto de evidencia. */
    private String rutaAdjunto;

    /** URL del PDF de esta versión. Nula si el archivado falló o el evento no tiene PDF. */
    private String rutaVersionPdf;

    /** id del adjunto de evidencia (adjunto_cotizacion), si lo hay. */
    private Long idAdjunto;
}