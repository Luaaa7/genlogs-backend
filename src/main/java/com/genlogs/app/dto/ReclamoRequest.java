package com.genlogs.app.dto;

import java.time.LocalDate;

import com.genlogs.app.model.PrioridadReclamo;
import com.genlogs.app.model.TipoReclamo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Alta y edición de un reclamo/incidencia. */
@Getter
@Setter
public class ReclamoRequest {

    @NotNull(message = "Debe indicar el tipo (RECLAMO o INCIDENCIA)")
    private TipoReclamo tipo;

    /** Si no se envía, se usa MEDIA. */
    private PrioridadReclamo prioridad;

    /** Obligatorio para RECLAMO (salvo que venga la orden de compra, de la que se toma el cliente). */
    private Long idCliente;

    private Long idOrdenCompra;

    private Long idUsuarioResponsable;

    @NotBlank(message = "Debe indicar el asunto")
    @Size(max = 200, message = "El asunto no puede superar 200 caracteres")
    private String asunto;

    @NotBlank(message = "Debe indicar la descripción")
    private String descripcion;

    private LocalDate fechaLimite;
}
