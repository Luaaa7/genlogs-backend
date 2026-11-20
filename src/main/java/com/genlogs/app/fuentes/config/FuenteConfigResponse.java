package com.genlogs.app.fuentes.config;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado de una tienda para la pantalla Configuración > Fuentes de búsqueda (L5, RF-88).
 * Nunca trae usuario, clave ni sesión: solo si están cargados.
 * reglasFaltantes (EF2, v3.4): las reglas mínimas que le faltan a la tienda (ITEM, NOMBRE,
 * PRECIO y ENLACE para buscar; USUARIO, CLAVE, ENVIAR y EXITO para ingresar). Vacía si
 * no falta ninguna. Con eso la pantalla muestra «Sin configurar» sin leer el texto del error.
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
        boolean tieneClave,
        List<String> reglasFaltantes) {
}