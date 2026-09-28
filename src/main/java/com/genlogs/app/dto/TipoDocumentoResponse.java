package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoDocumentoResponse {
    private Integer idTipoDocumento;
    private String codigoTipo;
    private String nombreTipo;
    private Short longitudDocumento;
    private Boolean soloNumerico;
}