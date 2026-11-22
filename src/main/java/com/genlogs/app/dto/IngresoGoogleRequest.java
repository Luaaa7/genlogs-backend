package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Token que entrega el botón «Continuar con Google» (ID token). */
@Getter
@Setter
public class IngresoGoogleRequest {

    @NotBlank(message = "Falta la respuesta de Google")
    @Size(max = 4096, message = "La respuesta de Google no es válida")
    private String credential;
}
