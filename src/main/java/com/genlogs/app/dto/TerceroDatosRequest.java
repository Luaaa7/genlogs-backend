package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Datos del tercero tal como los manda el frontend, anidados:
 * { "tercero": { "idTipoDocumento": 1, "numeroDocumento": "...", ... }, "idSectorEconomico": 3 }.
 * ClienteRequest y ProveedorRequest los copian a sus campos planos.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TerceroDatosRequest {
    private Integer idTipoDocumento;
    private String numeroDocumento;
    private String razonSocial;
    private Integer idDistrito;
    private String direccion;
    private String telefono;
    private String correo;
}