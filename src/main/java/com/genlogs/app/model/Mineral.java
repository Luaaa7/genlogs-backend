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

@Entity
@Table(name = "mineral")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Mineral extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mineral")
    private Integer idMineral;

    @Column(name = "nombre_mineral", length = 60, nullable = false, unique = true)
    private String nombreMineral;

    /** Ej: Au, Ag, Cu, Zn, Pb, Fe, Sn, Mo. Puede ser nulo. */
    @Column(name = "simbolo", length = 5)
    private String simbolo;
}
