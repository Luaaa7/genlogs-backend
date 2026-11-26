package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Datos de la empresa que el portal pide antes de la primera cotización (cuentas que entraron con Google). */
@Getter
@Setter
public class CompletarEmpresaRequest {

    /** "RUC" (por defecto) o "DNI" (persona natural, sin razón social). */
    @Pattern(regexp = "(?i)^(RUC|DNI)$", message = "El tipo de documento debe ser RUC o DNI")
    private String tipoDocumento;

    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento es demasiado largo")
    private String ruc;

    /** Obligatoria con RUC; con DNI se usa el nombre de contacto (lo valida el servicio). */
    @Size(max = 200, message = "La razón social es demasiado larga")
    private String razonSocial;

    @NotBlank(message = "La persona de contacto es obligatoria")
    @Size(max = 150, message = "El nombre de contacto es demasiado largo")
    private String nombreContacto;

    @Size(max = 30, message = "El teléfono es demasiado largo")
    private String telefono;
}
