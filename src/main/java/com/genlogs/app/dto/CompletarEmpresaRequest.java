package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Datos de la empresa que el portal pide antes de la primera cotización (cuentas que entraron con Google). */
@Getter
@Setter
public class CompletarEmpresaRequest {

    @NotBlank(message = "El RUC es obligatorio")
    @Size(max = 20, message = "El RUC es demasiado largo")
    private String ruc;

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 200, message = "La razón social es demasiado larga")
    private String razonSocial;

    @NotBlank(message = "La persona de contacto es obligatoria")
    @Size(max = 150, message = "El nombre de contacto es demasiado largo")
    private String nombreContacto;

    @Size(max = 30, message = "El teléfono es demasiado largo")
    private String telefono;
}
