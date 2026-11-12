package com.genlogs.app.enums;

/**
 * servicio.estado_aprobacion. Lo define el margen contra MARGEN_SERV_MIN y MARGEN_SERV_MAX.
 * PENDIENTE_APROBACION pasa a APROBADO o RECHAZADO por decisión del Jefe Administrativo;
 * al editar el servicio se vuelve a calcular.
 */
public enum EstadoAprobacionServicio {
    APROBADO,
    PENDIENTE_APROBACION,
    RECHAZADO
}
