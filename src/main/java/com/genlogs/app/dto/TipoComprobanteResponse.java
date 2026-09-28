package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class TipoComprobanteResponse {
    private Integer idTipoComprobante;
    private String codigoTipo;
    private String nombreTipo;
    private String seriePrefijo;
    private Boolean requiereRuc;
}
