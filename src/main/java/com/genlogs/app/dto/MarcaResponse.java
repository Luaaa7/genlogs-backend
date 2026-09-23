package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarcaResponse {
    private Integer idMarca;
    private String nombreMarca;
    private String pais;        // nombre_pais
    private String sitioWeb;
}
