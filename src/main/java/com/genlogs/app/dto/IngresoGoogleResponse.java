package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Respuesta del ingreso con Google. perfilCompleto = false: la cuenta todavía no tiene
 * empresa y el portal pide sus datos antes de la primera cotización.
 * pendiente = true: no hay token; el acceso espera la activación del Jefe Administrativo.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngresoGoogleResponse {

    private String token;
    private String nombreUsuario;
    private String nombre;
    private String nombreRol;
    private boolean perfilCompleto;
    private boolean pendiente;
    private String mensaje;

    public static IngresoGoogleResponse conAcceso(String token, String nombreUsuario, String nombre,
            String nombreRol, boolean perfilCompleto) {
        return new IngresoGoogleResponse(token, nombreUsuario, nombre, nombreRol, perfilCompleto, false, null);
    }

    public static IngresoGoogleResponse pendiente(String nombre) {
        return new IngresoGoogleResponse(null, null, nombre, null, false, true,
                "Tu acceso al portal está pendiente: GENLOGS lo revisará y te avisaremos por correo cuando esté habilitado.");
    }
}
