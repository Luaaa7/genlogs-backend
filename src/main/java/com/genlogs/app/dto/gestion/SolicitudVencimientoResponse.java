package com.genlogs.app.dto.gestion;

import com.genlogs.app.service.VencimientoSolicitudService.Semaforo;

/**
 * Fila de la lista interna por vencimiento. porcentajePlazo = cuánto del plazo hábil ya se usó
 * (puede pasar de 100 si está vencida; null si está pausada o no tiene plazo).
 */
public record SolicitudVencimientoResponse(SolicitudInternaResponse solicitud, Semaforo semaforo,
        Integer porcentajePlazo) {
}
