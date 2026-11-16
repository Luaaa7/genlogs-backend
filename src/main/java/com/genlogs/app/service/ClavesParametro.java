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
    /**
     * R1 (v3.3): consultas por tienda que puede usar la verificación de las 06:10; el cupo real es
     * min(este valor, limite_diario / 5). Valor inicial 10 (v3_3.sql); si falta la fila, se usa 10.
     */
    public static final String VERIFICACION_MAX_DIA = "VERIFICACION_MAX_DIA";

    /** Márgenes de repuestos (%). Al editarlos debe cumplirse MIN <= DEFAULT <= MAX. */
    public static final String MARGEN_REP_MIN = "MARGEN_REP_MIN";
    public static final String MARGEN_REP_DEFAULT = "MARGEN_REP_DEFAULT";
    public static final String MARGEN_REP_MAX = "MARGEN_REP_MAX";

    /**
     * Días para reutilizar una búsqueda del mismo N.º de parte (BPMN 9-B tarea 2).
     * Valor inicial 7. Si la fila aún no está en parametro_sistema, el servicio usa 7.
     */
    public static final String BUSQUEDA_REUSO_DIAS = "BUSQUEDA_REUSO_DIAS";
}