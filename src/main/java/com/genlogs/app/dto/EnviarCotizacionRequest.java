package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de POST /api/cotizaciones/{id}/enviar.
 * Espejo de EnviarCotizacionRequest en el frontend (cotizacion.types.ts).
 */
@Getter
@Setter
public class EnviarCotizacionRequest {

    @NotBlank(message = "Debe indicar el correo del destinatario")
    @Email(message = "El correo del destinatario no es válido")
    private String correoDestinatario;

    @NotBlank(message = "Debe indicar el asunto")
    private String asunto;

    private String mensaje;

    private boolean incluirDetalles;
}
