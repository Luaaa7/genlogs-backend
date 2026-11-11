package com.genlogs.app.enums;

/**
 * estado_cotizacion.codigo_estado. Es una tabla de catalogo: esta enumeracion solo
 * evita escribir los textos a mano. Coincide con EstadoCotizacion de
 * src/types/cotizacion.types.ts en el frontend.
 */
public enum CodigoEstadoCotizacion {
    BORRADOR,
    ENVIADA,
    EN_NEGOCIACION,
    APROBADA,
    RECHAZADA,
    VENCIDA,
    ANULADA
}