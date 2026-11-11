package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Zona de cobertura que muestra la landing. Sin datos del cliente ni coordenadas internas. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoberturaPublicaResponse {

    private Long id;
    private String nombre;
    private String region;
    private String descripcion;
}
