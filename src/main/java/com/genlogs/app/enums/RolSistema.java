package com.genlogs.app.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Los 7 roles de la v3.2 (Paso 0). Los textos coinciden con rol.nombre_rol y con
 * src/lib/permisos.ts del frontend. Spring les antepone ROLE_.
 */
public enum RolSistema {
    GERENTE_GENERAL,
    JEFE_ADMINISTRATIVO,
    CONTADOR_GENERAL,
    SUPERVISOR_SST,
    ASISTENTE_LOGISTICO,
    ASISTENTE_ADMINISTRATIVO,
    CLIENTE;

    /** Los 6 roles del personal de GENLOGS (todos menos CLIENTE). */
    public static List<RolSistema> internos() {
        return Arrays.stream(values()).filter(r -> r != CLIENTE).toList();
    }

    /** Nombres de los roles internos, listos para hasAnyRole(...). */
    public static String[] nombresInternos() {
        return internos().stream().map(Enum::name).toArray(String[]::new);
    }
}