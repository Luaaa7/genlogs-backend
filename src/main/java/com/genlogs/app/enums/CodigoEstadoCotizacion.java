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
    ANULADA,
    /** El asistente logístico la dejó para que el jefe la revise. */
    PENDIENTE_APROBACION,
    /** El jefe ya la aprobó. Recién aquí se puede enviar al cliente. */
    APROBADA_INTERNA
}