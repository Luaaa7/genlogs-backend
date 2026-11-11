package com.genlogs.app.enums;

/**
 * notificacion.tipo (v3_2_be_d.sql). La columna no tiene CHECK: si un bloque
 * necesita un tipo nuevo, lo agrega aqui en su propio PR.
 */
public enum TipoAviso {
    /** BE-C: cotizacion que supera MONTO_ALTO_VALOR_PEN (aviso al Gerente General). */
    ALTO_VALOR,
    /** BE-B: solicitud al 75 % de su plazo interno. */
    PLAZO_POR_VENCER,
    /** BE-B: solicitud con el plazo interno vencido. */
    PLAZO_VENCIDO,
    /** BE-D: factura vencida o recordatorio de pago. */
    FACTURA_VENCIDA,
    /** BE-D: fallo la consulta del tipo de cambio del dia. */
    TIPO_CAMBIO,
    /** BE-D: documento por vencer. */
    DOCUMENTO_POR_VENCER,
    /** BE-E: repuesto con margen fuera de rango, queda en BORRADOR (D-26). */
    MARGEN_FUERA_RANGO,
    GENERAL
}