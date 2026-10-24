package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para el catálogo de distritos.
 * Incluye provincia y departamento para distinguir distritos con nombres repetidos
 * (p.ej. "San Juan de Lurigancho" existe en Lima y en otras provincias).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistritoResponse {

    private Integer idDistrito;
    private String nombreDistrito;
    private String ubigeo;
    private Integer idProvincia;
    private String nombreProvincia;
    private Integer idDepartamento;
    private String nombreDepartamento;
}