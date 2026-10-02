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
    private String status;

    // Nombres (catálogo web)
    private String categoria;
    private String marca;              // null si el producto no tiene marca
    private String unidadMedida;
    private String imagenPrincipal;    // url_imagen de la imagen marcada como principal

    // Campos que usa el panel interno (listado, detalle y edición)
    private Integer idCategoriaProducto;
    private String categoriaNombre;
    private Integer idMarca;
    private String marcaNombre;
    private Integer idUnidadMedida;

    private List<CaracteristicaValorResponse> caracteristicas;
    private List<ImagenResponse> imagenes;
    private List<DocumentoResponse> documentos;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaracteristicaValorResponse {
        private Integer idCaracteristica;
        private String nombreCaracteristica;
        private String unidadCaracteristica;
        private String valor;
        private String valorCaracteristica; // igual a "valor"; es el nombre que lee el frontend
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImagenResponse {
        private Long idProductoImagen;
        private String urlImagen;
        private Boolean esPrincipal;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentoResponse {
        private Long idDocumento;
        private String tipoDocumento;
        private String nombreDocumento;
        private String urlDocumento;
    }
}