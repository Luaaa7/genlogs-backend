package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Respuesta unificada de la consulta de RUC/DNI contra Factiliza (RF-08).
 * El frontend la usa para autocompletar el formulario de cliente/proveedor:
 * razón social o nombres y apellidos, dirección y distrito. El distrito se
 * obtiene buscando el UBIGEO que devuelve Factiliza en el catálogo de
 * GenLogs; si no se encuentra, idDistrito llega vacío y el usuario lo elige.
 */
@Getter
@Builder
@AllArgsConstructor
public class ConsultaDocumentoResponse {

    private String numeroDocumento;

    /** "RUC" o "DNI", según la longitud del número consultado. */
    private String tipoDocumento;

    /** Razón social (RUC) o nombre completo (DNI). */
    private String razonSocial;

    /** Solo DNI: nombres y apellidos por separado. */
    private String nombres;

    private String apellidoPaterno;

    private String apellidoMaterno;

    private String direccion;

    /** UBIGEO de 6 dígitos del domicilio, si Factiliza lo devuelve. */
    private String ubigeo;

    /** Distrito del catálogo de GenLogs que corresponde al UBIGEO (o null). */
    private Integer idDistrito;

    /**
     * true cuando no hay FACTILIZA_API_KEY configurada y la respuesta es
     * simulada (modo demo), para que el frontend pueda avisarlo si quiere.
     */
    private boolean simulado;
}