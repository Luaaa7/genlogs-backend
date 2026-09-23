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

@Entity
@Table(name = "categoria_servicio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class CategoriaServicio extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_servicio")
    private Integer idCategoriaServicio;

    /** Categoría padre, para armar árbol de categorías. Nula si es de nivel raíz. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_padre")
    private CategoriaServicio categoriaPadre;

    @Column(name = "nombre_categoria", length = 120, nullable = false)
    private String nombreCategoria;

    /** Usado para URLs amigables en la web (ej: "servicios-de-transporte"). */
    @Column(name = "slug_web", length = 150, nullable = false, unique = true)
    private String slugWeb;
}
