package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearSolicitudRequest {

    @NotBlank(message = "El asunto es obligatorio")
    @Size(max = 150, message = "El asunto es demasiado largo")
    private String asunto;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 10, max = 4000, message = "Describe tu necesidad con al menos 10 caracteres")
    private String descripcion;

    private List<@Valid Item> items;

    @Getter
    @Setter
    public static class Item {

        @NotBlank(message = "La descripción del ítem es obligatoria")
        @Size(max = 255, message = "La descripción del ítem es demasiado larga")
        private String descripcion;

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2, message = "La cantidad tiene demasiados dígitos")
        private BigDecimal cantidad;

        @Size(max = 30, message = "La unidad es demasiado larga")
        private String unidad;
    }
}
