package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Sin token: el acceso queda pendiente hasta que un administrador active al usuario. */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistroClienteResponse {

    private String mensaje;
    private String token;
    private String nombre;
    private String nombreUsuario;

    public static RegistroClienteResponse pendiente() {
        return new RegistroClienteResponse(
                "Registro recibido. Ya puedes ingresar cuando tu acceso sea habilitado.",
                null,
                null,
                null);
    }
}
