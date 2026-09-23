package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** DTO de entrada para dar de alta o actualizar un producto del catálogo. */
@Getter
@Setter
public class ProductoRequest {

    @NotNull(message = "Debe indicar la categoría del producto")
    private Integer idCategoriaProducto;

    /** Opcional: NULL para productos sin marca (fabricación a medida). */
    private Integer idMarca;

    @NotNull(message = "Debe indicar la unidad de medida")
    private Integer idUnidadMedida;

    @NotBlank(message = "El código del producto es obligatorio")
    @Size(max = 30)
    private String codigoProducto;

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 200)
    private String nombreProducto;

    @Size(max = 100)
    private String procedencia;

    @NotNull(message = "Debe indicar si el producto es visible en la web")
    private Boolean visibleWeb = true;

    private String descripcion;
}
