package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** GET /api/facturacion/sunat: cómo está configurada la emisión (para mostrarlo en pantalla). */
@Getter
@AllArgsConstructor
public class SunatConfigResponse {
    /** SIMULADO o PROVEEDOR. */
    private String modo;
    private boolean simulado;
    private String serieFactura;
    private String serieBoleta;
}