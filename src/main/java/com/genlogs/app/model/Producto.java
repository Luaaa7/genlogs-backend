package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Producto del catálogo B2B (manufactura, minería e industrial a demanda).
 * id_marca es opcional: NULL para productos sin marca (p. ej. de
 * fabricación a medida).
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Producto extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long idProducto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_categoria_producto", nullable = false)
    private CategoriaProducto categoriaProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_marca")
    private Marca marca;

    // [PENDIENTE] UnidadMedida es un catálogo general (Módulo 3 -
    // Catálogos Generales, aún sin asignar en el tablero). Si al
    // compilar todavía no existe com.genlogs.app.model.UnidadMedida,
    // coordina con el equipo para no bloquear tu rama.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_unidad_medida", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(name = "codigo_producto", nullable = false, unique = true, length = 30)
    private String codigoProducto;

    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;

    @Column(name = "procedencia", length = 100)
    private String procedencia;

    @Column(name = "visible_web", nullable = false)
    @Builder.Default
    private Boolean visibleWeb = true;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;
}
