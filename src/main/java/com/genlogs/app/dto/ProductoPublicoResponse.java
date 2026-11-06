package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Catálogo de la landing. Sin precios, márgenes ni proveedores. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductoPublicoResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private String categoria;
    private String unidad;
    private String imagenUrl;
}
