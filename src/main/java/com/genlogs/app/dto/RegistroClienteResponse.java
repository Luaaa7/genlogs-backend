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

    /**
     * La empresa ya es cliente: el usuario nuevo queda pendiente (sin token) hasta que
     * lo active el Jefe Administrativo. Así nadie entra a los pedidos y precios de una
     * empresa con solo saber su RUC, que es público.
     */
    public static RegistroClienteResponse pendiente(String nombre) {
        return new RegistroClienteResponse(
                "Tu empresa ya tiene cuenta en el portal. Tu acceso quedó pendiente: "
                        + "GENLOGS lo revisará y te avisaremos por correo cuando esté habilitado.",
                null,
                nombre,
                null);
    }

    public static RegistroClienteResponse conAcceso(String token, String nombre, String nombreUsuario) {
        return new RegistroClienteResponse(
                "Registro completado. Ya puedes usar el portal.",
                token,
                nombre,
                nombreUsuario);
    }
}
