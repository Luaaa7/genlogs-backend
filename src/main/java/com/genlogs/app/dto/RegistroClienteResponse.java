package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Registro con acceso inmediato (D-10): trae el token del cliente para entrar sin esperar. */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistroClienteResponse {

    private String mensaje;
    private String token;
    private String nombre;
    private String nombreUsuario;

    public static RegistroClienteResponse conAcceso(String token, String nombre, String nombreUsuario) {
        return new RegistroClienteResponse(
                "Registro completado. Ya puedes usar el portal.",
                token,
                nombre,
                nombreUsuario);
    }
}
