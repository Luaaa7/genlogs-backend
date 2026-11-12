package com.genlogs.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** POST /api/portal/reclamos/{id}/calificacion (RF-70): de 1 a 5, con comentario opcional. */
@Getter
@Setter
public class CalificacionReclamoRequest {

    @NotNull(message = "Debe indicar la calificación")
    @Min(value = 1, message = "La calificación va de 1 a 5")
    @Max(value = 5, message = "La calificación va de 1 a 5")
    private Integer puntaje;

    @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres")
    private String comentario;
}
