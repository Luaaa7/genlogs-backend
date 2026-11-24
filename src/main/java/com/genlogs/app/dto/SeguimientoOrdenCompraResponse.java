package com.genlogs.app.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evento del seguimiento de una orden de compra. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeguimientoOrdenCompraResponse {

    private Long idSeguimiento;

    /** ORDEN_CREADA, CAMBIO_ESTADO, ARCHIVO_OC u OC_CLIENTE. */
    private String tipoEvento;

    /** Código del estado previo. Solo en los cambios de estado. */
    private String estadoAnterior;

    /** Código del estado de la orden en ese momento. */
    private String estadoNuevo;

    private String usuario;

    private Instant fechaEvento;

    private String comentario;
}