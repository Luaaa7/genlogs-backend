package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Alta del portal. El rol y el cliente los resuelve el servidor; el body no los trae. */
@Getter
@Setter
public class RegistroClienteRequest {

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 200, message = "La razón social es demasiado larga")
    private String razonSocial;

    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento es demasiado largo")
    private String documento;

    @NotBlank(message = "La persona de contacto es obligatoria")
    @Size(max = 150, message = "El nombre de contacto es demasiado largo")
    private String nombreContacto;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo inválido")
    @Size(max = 150, message = "El correo es demasiado largo")
    private String correo;

    @Size(max = 30, message = "El teléfono es demasiado largo")
    private String telefono;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;
}
