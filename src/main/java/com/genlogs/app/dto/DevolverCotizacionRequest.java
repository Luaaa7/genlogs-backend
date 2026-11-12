package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Motivo obligatorio al devolver una cotización pendiente al asistente. */
@Getter
@Setter
public class DevolverCotizacionRequest {

    @NotBlank(message = "Indica el motivo para devolver la cotización")
    private String motivo;
}
