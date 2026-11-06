package com.genlogs.app.dto;

import com.genlogs.app.model.EstadoReclamo;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEstadoReclamoRequest {

    @NotNull(message = "Debe indicar el nuevo estado")
    private EstadoReclamo estado;

    /** Comentario del seguimiento. Obligatorio al pasar a RESUELTO (es la solución) o ANULADO (motivo). */
    private String comentario;
}
