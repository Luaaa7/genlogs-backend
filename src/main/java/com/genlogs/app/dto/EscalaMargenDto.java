package com.genlogs.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Datos de la escala de márgenes que viajan entre el frontend y el backend. */
public final class EscalaMargenDto {

    private EscalaMargenDto() {
    }

    /** Un rango de costo de productos. costoHasta vacío = "en adelante". */
    public record RangoProducto(
            @NotNull(message = "Indica el costo desde")
            @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
            BigDecimal costoDesde,
            BigDecimal costoHasta,
            @NotNull(message = "Indica el margen sugerido")
            @DecimalMin(value = "0.00", message = "El margen no puede ser negativo")
            @DecimalMax(value = "90.00", message = "El margen no puede pasar de 90 %")
            BigDecimal margenSugerido,
            @NotNull(message = "Indica el margen mínimo")
            @DecimalMin(value = "0.00", message = "El margen no puede ser negativo")
            @DecimalMax(value = "90.00", message = "El margen no puede pasar de 90 %")
            BigDecimal margenMinimo) {
    }

    /** Margen de una categoría de servicio. idCategoriaServicio vacío = margen por defecto. */
    public record MargenServicio(
            Integer idCategoriaServicio,
            String nombreCategoria,
            @NotNull(message = "Indica el margen sugerido")
            @DecimalMin(value = "0.00", message = "El margen no puede ser negativo")
            @DecimalMax(value = "90.00", message = "El margen no puede pasar de 90 %")
            BigDecimal margenSugerido,
            @NotNull(message = "Indica el margen mínimo")
            @DecimalMin(value = "0.00", message = "El margen no puede ser negativo")
            @DecimalMax(value = "90.00", message = "El margen no puede pasar de 90 %")
            BigDecimal margenMinimo) {
    }

    /** Escala completa: se lee y se guarda entera. */
    public record Escala(
            @NotNull @Valid List<RangoProducto> productos,
            @NotNull @Valid List<MargenServicio> servicios) {
    }
}
