package com.genlogs.app.enums;

/** CHECK ck_solicitud_cotizacion_estado (v3_2.sql). El cliente solo ve la barra de estados. */
public enum EstadoSolicitud {
    PENDIENTE,
    EN_REVISION,
    EN_ESPERA_TERCERO,
    COTIZADA,
    RECHAZADA,
    CANCELADA
}