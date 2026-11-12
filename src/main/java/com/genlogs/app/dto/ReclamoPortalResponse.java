package com.genlogs.app.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reclamo tal como lo ve el cliente en el portal (RF-70). No trae datos
 * internos: ni responsable, ni prioridad, ni fecha límite, ni comentarios del
 * personal; del historial solo los cambios de estado.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReclamoPortalResponse {

    private Long idReclamo;
    private String codigoReclamo;
    private String estado;
    private String asunto;
    private String descripcion;
    private String numeroOrdenCompra;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaCierre;
    /** Solución que registró GENLOGS al resolverlo. */
    private String solucion;
    /** 1 a 5; null si todavía no califica. */
    private Integer calificacion;
    private String comentarioCalificacion;
    /** true si está RESUELTO o CERRADO y aún no tiene calificación. */
    private boolean puedeCalificar;
    /** Solo en el detalle. */
    private List<Evento> historial;

    @Getter
    @AllArgsConstructor
    public static class Evento {
        private String estado;
        private LocalDateTime fecha;
    }
}
