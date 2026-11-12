package com.genlogs.app.service;

/**
 * {@code @PreAuthorize} de despachos.
 * Ver: Gerente, Jefe, Asistente Administrativo, Asistente Logístico y Supervisor SST.
 * Crear y avanzar: solo el Asistente Logístico. El bloqueo de pase y SCTR no se salta.
 * Confirmar pase y SCTR: solo el Supervisor SST.
 * El Contador no entra. El cliente solo usa el portal.
 */
public final class PermisosDespacho {

    public static final String VER = "hasAnyRole("
            + "T(com.genlogs.app.enums.RolSistema).GERENTE_GENERAL.name(),"
            + "T(com.genlogs.app.enums.RolSistema).JEFE_ADMINISTRATIVO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_ADMINISTRATIVO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).SUPERVISOR_SST.name())";

    public static final String GESTIONAR =
            "hasRole(T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name())";

    public static final String PORTAL =
            "hasRole(T(com.genlogs.app.enums.RolSistema).CLIENTE.name())";

    /** Confirmar pase y SCTR. No avanza etapas. */
    public static final String APROBAR_SST =
            "hasRole(T(com.genlogs.app.enums.RolSistema).SUPERVISOR_SST.name())";

    private PermisosDespacho() {
    }
}
