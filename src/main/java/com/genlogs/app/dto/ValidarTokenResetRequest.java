package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidarTokenResetRequest {

    @NotBlank(message = "El token es obligatorio")
    private String token;
}