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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Categoría (jerárquica) del catálogo web de productos.
 * id_categoria_padre es NULL para una categoría raíz. El campo
 * "nivel" (1 = raíz, hijo = nivel del padre + 1) lo calcula el
 * trigger fn_categoria_producto_nivel() en la base de datos: no debe
 * asignarse manualmente desde el backend.
 */
@Entity
@Table(name = "categoria_producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class CategoriaProducto extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_producto")
    private Integer idCategoriaProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_padre")
    private CategoriaProducto categoriaPadre;

    @Column(name = "nombre_categoria", nullable = false, length = 120)
    private String nombreCategoria;

    /** Slug para la URL pública del catálogo web, ej. "valvulas-industriales". */
    @Column(name = "slug_web", nullable = false, unique = true, length = 150)
    private String slugWeb;

    /** Calculado por trigger de BD; no asignar manualmente al crear/editar. */
    @Column(name = "nivel", nullable = false)
    private Short nivel;
}
