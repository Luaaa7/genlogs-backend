package com.genlogs.app.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReclamoResponse {
    private Long idReclamo;
    private String codigoReclamo;
    private String tipo;
    private String estado;
    private String prioridad;
    private Long idCliente;
    private String cliente;
    private Long idOrdenCompra;
    private String numeroOrdenCompra;
    private Long idUsuarioResponsable;
    private String responsable;
    private String asunto;
    private String descripcion;
    private LocalDateTime fechaRegistro;
    private LocalDate fechaLimite;
    private LocalDateTime fechaCierre;
    private String solucion;
    /** RF-70: calificación del cliente (1 a 5) y su comentario; null si no calificó. */
    private Integer calificacion;
    private String comentarioCalificacion;
    /** true si pasó la fecha límite y aún no está resuelto/cerrado/anulado. */
    private Boolean vencido;
    /** Estados a los que puede pasar desde el actual (para los botones del frontend). */
    private List<String> siguientesEstados;
    /** Solo en el detalle (GET /{id}). */
    private List<SeguimientoReclamoResponse> seguimientos;
}
