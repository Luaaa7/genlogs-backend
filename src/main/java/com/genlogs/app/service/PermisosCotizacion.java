package com.genlogs.app.service;

/**
 * Expresiones de {@code @PreAuthorize} del bloque de cotizaciones.
 * Los nombres salen de {@link com.genlogs.app.enums.RolSistema}; no hay roles nuevos.
 *
 * Ver: Gerente, Jefe, Asistente Logístico y Asistente Administrativo.
 * Crear y editar: Asistente Logístico y Jefe Administrativo (decisión del equipo, 13/11/2026:
 * el Jefe también cotiza). Aprobar (cambio de estado): Jefe Administrativo.
 * Contador, Supervisor SST y Cliente no entran a {@code /api/cotizaciones}.
 */
public final class PermisosCotizacion {

    public static final String VER = "hasAnyRole("
            + "T(com.genlogs.app.enums.RolSistema).GERENTE_GENERAL.name(),"
            + "T(com.genlogs.app.enums.RolSistema).JEFE_ADMINISTRATIVO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_ADMINISTRATIVO.name())";

    public static final String CREAR = "hasAnyRole("
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).JEFE_ADMINISTRATIVO.name())";

    public static final String APROBAR =
            "hasRole(T(com.genlogs.app.enums.RolSistema).JEFE_ADMINISTRATIVO.name())";

    /** Portal: el cliente solo ve y decide las cotizaciones de su empresa. */
    public static final String PORTAL =
            "hasRole(T(com.genlogs.app.enums.RolSistema).CLIENTE.name())";

    private PermisosCotizacion() {
    }
}