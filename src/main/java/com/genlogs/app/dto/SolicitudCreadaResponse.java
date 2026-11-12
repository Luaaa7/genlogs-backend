package com.genlogs.app.dto;

import com.genlogs.app.enums.EstadoSolicitud;

/**
 * Respuesta 201 de POST /api/solicitudes (Paso 0). Sin fechas ni plazos: el
 * cliente solo ve estados (D-14).
 */
public record SolicitudCreadaResponse(
        Long idSolicitud,
        String codigoSolicitud,
        EstadoSolicitud estado) {
}