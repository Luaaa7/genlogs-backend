package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEstadoOrdenCompraRequest {

    @NotBlank(message = "Debe indicar el código del nuevo estado")
    private String codigoEstado;

    private String observaciones;
}
