package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

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

    /**
     * Forma nueva: "aceptó", "pidió_cambios" o "rechazó".
     * Si viene, este cuerpo reemplaza a codigoEstado.
     */
    private String accion;

    /** Día de la respuesta del cliente (aaaa-mm-dd). Obligatorio en la forma nueva. */
    @JsonAlias({"fecha_respuesta", "fechaRespuesta"})
    private String fechaRespuesta;

    /** correo, whatsapp, llamada u otro. */
    private String medio;

    /** Nota o motivo. En la forma nueva reemplaza a comentario. */
    private String nota;
}
