package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEstadoFacturacionRequest {

    @NotBlank(message = "Debe indicar el código del nuevo estado")
    private String codigoEstado;
}
