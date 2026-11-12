package com.genlogs.app.dto.gestion;

import java.time.LocalDateTime;

/** Una línea del historial de la solicitud (solicitud_evento). usuario = nombre de usuario o null. */
public record EventoSolicitudResponse(String tipo, String detalle, String usuario, LocalDateTime fecha) {
}
