package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Formulario «Contáctame» de la web (BR2). El texto del aviso se arma en el servidor. */
@Getter
@Setter
public class ContactoPublicoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre es demasiado largo")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo inválido")
    @Size(max = 150, message = "El correo es demasiado largo")
    private String correo;

    @Size(max = 30, message = "El teléfono es demasiado largo")
    private String telefono;

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(max = 2000, message = "El mensaje es demasiado largo")
    private String mensaje;

    /**
     * Honeypot. Las personas no lo ven; un bot suele llenarlo.
     * Si viene con texto, se responde enviado y no se envía nada.
     */
    @Size(max = 200)
    private String sitioWeb;
}
