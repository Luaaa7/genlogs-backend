package com.genlogs.app.dto.gestion;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vista interna de una solicitud: con plazo, pausa y responsable. Nunca se manda al
 * cliente, que solo ve estados (D-14).
 */
public record SolicitudInternaResponse(
        Long idSolicitud,
        String codigoSolicitud,
        String asunto,
        String cliente,
        String estado,
        String tipoPlazo,
        LocalDateTime fechaRecepcion,
        LocalDateTime fechaLimiteInterna,
        LocalDateTime pausadaDesde,
        String motivoPausa,
        LocalDate fechaEstimadaTercero,
        Short reclasificaciones,
        UsuarioResumen asignado) {

    public record UsuarioResumen(Long idUsuario, String nombreUsuario, String nombres) {
    }
}
