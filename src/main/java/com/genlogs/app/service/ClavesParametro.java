package com.genlogs.app.service;

/**
 * Claves de parametro_sistema (v3_2.sql). Usalas con ParametrosService.get(...)
 * en vez de escribir el texto a mano. Si un bloque agrega claves (p. ej. BE-D con
 * MARGEN_REP_*, D-17), las suma aqui en su PR junto con su INSERT en v3_2_be_x.sql.
 */
public final class ClavesParametro {

    private ClavesParametro() {
    }

    /** Desde este monto (en soles) se notifica al Gerente General. */
    public static final String MONTO_ALTO_VALOR_PEN = "MONTO_ALTO_VALOR_PEN";

    public static final String MARGEN_SERV_SUGERIDO = "MARGEN_SERV_SUGERIDO";
    public static final String MARGEN_SERV_MIN = "MARGEN_SERV_MIN";
    public static final String MARGEN_SERV_AVISO = "MARGEN_SERV_AVISO";
    public static final String MARGEN_SERV_MAX = "MARGEN_SERV_MAX";

    public static final String PLAZO_COMUN_DIAS = "PLAZO_COMUN_DIAS";
    public static final String PLAZO_IMPORTADO_DIAS = "PLAZO_IMPORTADO_DIAS";
    public static final String PLAZO_ESPECIAL_DIAS = "PLAZO_ESPECIAL_DIAS";
    public static final String ALERTA_PLAZO_PCT = "ALERTA_PLAZO_PCT";

    public static final String VERIFICACION_DIAS = "VERIFICACION_DIAS";
    public static final String OCULTAR_SIN_VERIF_DIAS = "OCULTAR_SIN_VERIF_DIAS";
    public static final String OCULTAR_FALLAS = "OCULTAR_FALLAS";
}