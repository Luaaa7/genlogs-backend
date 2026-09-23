package com.genlogs.app.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoResponse {
    private Long idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private String procedencia;
    private String descripcion;
    private Boolean visibleWeb;
    private String categoria;
    private String marca;              // null si el producto no tiene marca
    private String unidadMedida;
    private String imagenPrincipal;    // url_imagen de la imagen marcada como principal
    private List<CaracteristicaValorResponse> caracteristicas;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaracteristicaValorResponse {
        private String nombreCaracteristica;
        private String unidadCaracteristica;
        private String valor;
    }
}
