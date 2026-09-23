package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo de características técnicas disponibles para la ficha de un producto. */
@Entity
@Table(name = "caracteristica_tecnica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class CaracteristicaTecnica extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caracteristica")
    private Integer idCaracteristica;

    @Column(name = "nombre_caracteristica", nullable = false, unique = true, length = 100)
    private String nombreCaracteristica;

    /** Unidad de la ficha técnica, ej. "kg", "mm", "HP" (opcional). */
    @Column(name = "unidad_caracteristica", length = 30)
    private String unidadCaracteristica;
}
