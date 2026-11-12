package com.genlogs.app.dto;

import java.time.LocalDateTime;

/** Un aviso de la campanita (GET /api/notificaciones). */
public record NotificacionResponse(
        Long idNotificacion,
        String tipo,
        String titulo,
        String mensaje,
        String enlace,
        boolean leida,
        LocalDateTime fechaCreacion) {
}