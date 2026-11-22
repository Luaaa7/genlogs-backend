package com.genlogs.app.dto;

/** pendiente = true: la empresa ya era cliente y el acceso espera la activación del Jefe Administrativo. */
public record CompletarEmpresaResponse(boolean pendiente, String mensaje) {
}
