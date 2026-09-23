package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaProductoResponse {
    private Integer idCategoriaProducto;
    private Integer idCategoriaPadre;   // null si es una categoría raíz
    private String nombreCategoria;
    private String slugWeb;
    private Short nivel;                // 1 = raíz (lo calcula el trigger de la BD)
}
