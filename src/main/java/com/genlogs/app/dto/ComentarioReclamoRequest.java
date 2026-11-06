package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Nota de seguimiento sin cambio de estado. */
@Getter
@Setter
public class ComentarioReclamoRequest {

    @NotBlank(message = "Debe escribir un comentario")
    private String comentario;
}
