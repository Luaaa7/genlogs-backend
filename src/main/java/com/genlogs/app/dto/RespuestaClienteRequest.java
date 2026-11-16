package com.genlogs.app.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * El Jefe registra la respuesta que el cliente dio por correo o por teléfono.
 * El adjunto es uno ya cargado en la cotización (POST /adjuntos).
 */
@Getter
@Setter
public class RespuestaClienteRequest {

    /** Solo APROBADA o RECHAZADA. */
    private String codigoEstado;
    /** Cómo y cuándo respondió el cliente. */
    private String comentario;
    /** Adjunto nuevo de este registro: el correo o la OC de la minera. */
    private Long idAdjunto;
}
