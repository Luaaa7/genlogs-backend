package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Respuesta del ingreso del cliente. nombreRol siempre es CLIENTE. */
@Getter
@AllArgsConstructor
public class IngresoClienteResponse {

    private String token;
    private String nombreUsuario;
    /** Nombre del contacto, para mostrar en el portal. */
    private String nombre;
    private String nombreRol;
}
