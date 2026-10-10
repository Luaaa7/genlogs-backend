package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Respuesta unificada de la consulta de RUC/DNI contra Factiliza (RF-08).
 * El frontend usa razonSocial y direccion para autocompletar el formulario
 * de registro de cliente/proveedor; el campo distrito no se autocompleta
 * porque Factiliza no devuelve el id_distrito propio del catálogo de
 * GenLogs (solo texto de ubigeo), así que ese campo lo sigue eligiendo el
 * usuario manualmente.
 */
@Getter
@Builder
@AllArgsConstructor
public class ConsultaDocumentoResponse {

    private String numeroDocumento;

    /** "RUC" o "DNI", según la longitud del número consultado. */
    private String tipoDocumento;

    private String razonSocial;

    private String direccion;

    /**
     * true cuando no hay FACTILIZA_API_KEY configurada y la respuesta es
     * simulada (modo demo), para que el frontend pueda avisarlo si quiere.
     */
    private boolean simulado;
}
