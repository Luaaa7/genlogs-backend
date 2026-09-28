package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioRequest {

    @NotNull(message = "Debe indicar el rol")
    private Integer idRol;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 50)
    private String nombreUsuario;

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150)
    private String nombres;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo inválido")
    @Size(max = 150)
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "Las iniciales son obligatorias")
    @Size(max = 3)
    private String iniciales;
}