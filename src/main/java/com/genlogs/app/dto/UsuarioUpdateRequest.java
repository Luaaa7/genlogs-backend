package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioUpdateRequest {
    @NotNull(message = "Debe indicar el rol del usuario")
    private Integer idRol;

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 150)
    private String nombres;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo inválido")
    @Size(max = 150)
    private String correo;

    @NotBlank(message = "Las iniciales son obligatorias")
    @Size(max = 3)
    private String iniciales;
}