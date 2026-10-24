package com.genlogs.app.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Datos del tercero tal como los envía el frontend, anidados bajo la
 * clave "tercero" en ClienteRequest y ProveedorRequest.
 */
@Getter
@Setter
public class TerceroDatos {
    private Integer idTipoDocumento;
    private String numeroDocumento;
    private String razonSocial;
    private Integer idDistrito;
    private String direccion;
    private String telefono;
    private String correo;
}
