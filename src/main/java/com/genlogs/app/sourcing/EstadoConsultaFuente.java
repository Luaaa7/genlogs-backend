package com.genlogs.app.sourcing;

/**
 * Resultado de una vuelta del 9-B.1. Distinto del estado de la fuente ({@code fuente_busqueda.estado})
 * y del estado de la ficha ({@code historial_sourcing.estado}: CAPTURADO / ÚTIL / DESCARTADO / CONVERTIDO).
 * No hay columna estado_consulta en v3_2.sql: este valor vive en la respuesta del servicio.
 */
public enum EstadoConsultaFuente {
    OK,
    OMITIDA_CUOTA,
    OMITIDA_SESION,
    BLOQUEADA,
    CON_ERROR,
    REQUIERE_VERIFICACION,
    SIN_RESULTADOS,
    /** P2 no está cableado. No es CON_ERROR de la tienda. */
    NO_CONECTADO
}
