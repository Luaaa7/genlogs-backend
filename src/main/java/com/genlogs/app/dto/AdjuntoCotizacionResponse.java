package com.genlogs.app.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Adjunto de una cotización.
 *
 * Mantiene idAdjunto/nombreArchivo/urlArchivo y suma los alias que consume el
 * frontend (AdjuntoCotizacion en src/types/cotizacion.types.ts): urlCloudinary,
 * tipoArchivo, fechaSubida y subidoPor.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjuntoCotizacionResponse {
    private Long idAdjunto;
    /** Alias de idAdjunto. */
    private Long id;
    private String nombreArchivo;
    private String urlArchivo;
    /** La URL almacenada ya es la de Cloudinary. */
    private String urlCloudinary;
    /** Extensión del archivo (pdf, png, docx...). */
    private String tipoArchivo;
    /**
     * Tamaño del archivo en bytes. El frontend lo formatea con
     * formatearTamanioArchivo() sin validar: si llega undefined revienta con
     * "Cannot read properties of undefined (reading 'toFixed')" y tumba la
     * pantalla de detalle. SIEMPRE debe venir un número (0 si no se conoce).
     */
    private Long tamanio;
    private LocalDateTime fechaSubida;
    private String subidoPor;
}
