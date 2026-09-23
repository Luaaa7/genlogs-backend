package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** DTO para agregar o actualizar el valor de una característica técnica de un producto. */
@Getter
@Setter
public class ProductoCaracteristicaRequest {

    @NotNull(message = "Debe indicar la característica técnica")
    private Integer idCaracteristica;

    @NotBlank(message = "El valor de la característica es obligatorio")
    private String valorCaracteristica;
}
