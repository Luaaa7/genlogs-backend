package com.genlogs.app.dto;

import java.time.LocalDateTime;

/**
 * Acceso del portal que espera la revisión del Jefe Administrativo (BPMN 8.4).
 * origen: REGISTRO (se registró con el RUC de una empresa que ya es cliente) o
 * GOOGLE (entró con Google y dio ese RUC al pedir su primera cotización).
 */
public record AccesoPendienteResponse(
        Long idUsuario,
        String nombres,
        String correo,
        String empresa,
        String ruc,
        LocalDateTime fechaSolicitud,
        String origen) {
}
