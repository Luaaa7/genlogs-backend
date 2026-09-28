package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo inválido")
    private String correo;
}