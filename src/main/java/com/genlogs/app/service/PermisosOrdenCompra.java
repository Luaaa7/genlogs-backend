package com.genlogs.app.service;

/**
 * {@code @PreAuthorize} de órdenes de compra.
 * Ver: los cinco roles de consulta, más ADMINISTRADOR y VENDEDOR (no están en
 * {@link com.genlogs.app.enums.RolSistema} y ya entraban).
 * Crear, cambiar estado y subir el archivo: Asistente Administrativo, y se
 * conserva a ADMINISTRADOR y VENDEDOR. El jefe queda solo en consulta.
 * Confirmar lo leído: solo el Asistente Administrativo.
 */
public final class PermisosOrdenCompra {

    public static final String VER = "hasAnyRole("
            + "T(com.genlogs.app.enums.RolSistema).GERENTE_GENERAL.name(),"
            + "T(com.genlogs.app.enums.RolSistema).JEFE_ADMINISTRATIVO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).CONTADOR_GENERAL.name(),"
            + "T(com.genlogs.app.enums.RolSistema).SUPERVISOR_SST.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_LOGISTICO.name(),"
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_ADMINISTRATIVO.name(),"
            + "'ADMINISTRADOR','VENDEDOR')";

    public static final String GESTIONAR = "hasAnyRole("
            + "T(com.genlogs.app.enums.RolSistema).ASISTENTE_ADMINISTRATIVO.name(),"
            + "'ADMINISTRADOR','VENDEDOR')";

    public static final String CONFIRMAR =
            "hasRole(T(com.genlogs.app.enums.RolSistema).ASISTENTE_ADMINISTRATIVO.name())";

    private PermisosOrdenCompra() {
    }
}
