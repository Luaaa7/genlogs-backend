package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Escala de márgenes para cotizar (tabla escala_margen).
 *
 * El margen es sobre el precio de venta: precio = costo / (1 - margen / 100).
 * - PRODUCTO: el margen depende del costo unitario. Rige la fila con
 *   costoDesde <= costo < costoHasta (costoHasta vacío = "en adelante").
 * - SERVICIO: el margen depende de la categoría del servicio. La fila sin
 *   categoría es el margen por defecto.
 */
@Entity
@Table(name = "escala_margen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalaMargen {

    public static final String PRODUCTO = "PRODUCTO";
    public static final String SERVICIO = "SERVICIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_escala_margen")
    private Integer idEscalaMargen;

    /** "PRODUCTO" o "SERVICIO". */
    @Column(name = "tipo_item", nullable = false, length = 10)
    private String tipoItem;

    @Column(name = "costo_desde", precision = 14, scale = 2)
    private BigDecimal costoDesde;

    @Column(name = "costo_hasta", precision = 14, scale = 2)
    private BigDecimal costoHasta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_servicio")
    private CategoriaServicio categoriaServicio;

    @Column(name = "margen_sugerido", nullable = false, precision = 5, scale = 2)
    private BigDecimal margenSugerido;

    @Column(name = "margen_minimo", nullable = false, precision = 5, scale = 2)
    private BigDecimal margenMinimo;

    @Column(name = "user_update", length = 50)
    private String userUpdate;

    @Column(name = "date_update")
    private LocalDateTime dateUpdate;
}