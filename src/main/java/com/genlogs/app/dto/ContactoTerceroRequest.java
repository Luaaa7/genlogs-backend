package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactoTerceroRequest {

    @NotBlank(message = "El nombre del contacto es obligatorio")
    @Size(max = 150)
    private String nombres;

    @Size(max = 100)
    private String cargo;

    @Email(message = "Correo inválido")
    @Size(max = 150)
    private String correo;

    @Size(max = 30)
    private String telefono;

    private boolean esPrincipal;
}