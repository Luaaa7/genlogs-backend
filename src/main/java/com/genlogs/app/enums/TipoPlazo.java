package com.genlogs.app.enums;

/**
 * solicitud_cotizacion.tipo_plazo. Los dias habiles salen de parametro_sistema:
 * PLAZO_COMUN_DIAS (1), PLAZO_IMPORTADO_DIAS (3), PLAZO_ESPECIAL_DIAS (5).
 */
public enum TipoPlazo {
    /** Comun nacional con N. de parte. */
    COMUN,
    /** Importado o poco comun. */
    IMPORTADO,
    /** Solo foto o plano, o pieza a fabricar. */
    ESPECIAL
}