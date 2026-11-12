package com.genlogs.app.service;

/**
 * {@code @PreAuthorize} de despachos.
 * Ver: Gerente, Jefe y Asistente Administrativo, más el Asistente Logístico que los opera.
 * Crear y avanzar: solo el Asistente Logístico.
 * El Contador no entra. El Supervisor SST queda anotado para el pase y el SCTR (tarea 6), sin endpoint aquí.
 * El cliente solo usa el portal.
 */
public final class PermisosDespacho {

    public static final String VER = "hasAnyRole("
            + "T(com.genlogs.app.enums.RolSistema).GERENTE_GENERAL.name(),"
            + "T(com.genlogs.app.enums.RolSistema).JEFE_ADMINISTRATIVO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_ADMINISTRATIVO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name())";

    public static final String GESTIONAR =
            "hasRole(T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name())";

    public static final String PORTAL =
            "hasRole(T(com.genlogs.app.enums.RolSistema).CLIENTE.name())";

    /** Tarea 6: aprobar pase y SCTR. No hay endpoint que lo use todavía. */
    public static final String APROBAR_SST =
            "hasRole(T(com.genlogs.app.enums.RolSistema).SUPERVISOR_SST.name())";

    private PermisosDespacho() {
    }
}
