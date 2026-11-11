package com.genlogs.app.dto;

import java.util.List;

/**
 * Respuesta de GET /api/notificaciones: los ultimos avisos del usuario (mas
 * recientes primero) y cuantos no ha leido, para el contador de la campanita.
 */
public record AvisosResponse(
        long noLeidas,
        List<NotificacionResponse> avisos) {
}