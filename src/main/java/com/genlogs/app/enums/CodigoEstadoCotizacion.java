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
    /**
     * El cliente aceptó. Antes el código era APROBADA ("Aprobada"): se renombró
     * para no confundirlo con {@link #APROBADA_INTERNA} ("Aprobada por el Jefe").
     * El texto viejo sigue valiendo como alias de entrada.
     */
    ACEPTADA_CLIENTE,
    RECHAZADA,
    VENCIDA,
    ANULADA,
    /** El asistente logístico la dejó para que el jefe la revise. */
    PENDIENTE_APROBACION,
    /** El jefe ya la aprobó. Recién aquí se puede enviar al cliente. */
    APROBADA_INTERNA;

    /** ACEPTADA_CLIENTE, o el código viejo APROBADA si la migración aún no corrió. */
    public static boolean esAceptacionCliente(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        String limpio = codigo.trim();
        return ACEPTADA_CLIENTE.name().equalsIgnoreCase(limpio)
                || "APROBADA".equalsIgnoreCase(limpio);
    }
}