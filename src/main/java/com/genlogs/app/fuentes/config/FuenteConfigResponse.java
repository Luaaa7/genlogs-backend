package com.genlogs.app.fuentes.config;

import java.time.LocalDateTime;

/**
 * Estado de una tienda para la pantalla Configuración > Fuentes de búsqueda (L5, RF-88).
 * Nunca trae usuario, clave ni sesión: solo si están cargados.
 */
public record FuenteConfigResponse(
        String codigo,
        String nombre,
        String urlBase,
        boolean activa,
        String estado,
        LocalDateTime sesionValidaHasta,
        LocalDateTime ultimaPrueba,
        String ultimoError,
        int pausaSegundos,
        int limiteDiario,
        Short aciertosPrueba,
        boolean tieneUsuario,
        boolean tieneClave) {
}
