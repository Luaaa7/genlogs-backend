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
@Table(name = "moneda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Moneda extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_moneda")
    private Integer idMoneda;

    /** Ej: PEN, USD, EUR. La BD valida formato ^[A-Z]{3}$ con un CHECK. */
    @Column(name = "codigo_moneda", length = 3, nullable = false, unique = true)
    private String codigoMoneda;

    @Column(name = "nombre_moneda", length = 60, nullable = false, unique = true)
    private String nombreMoneda;

    @Column(name = "simbolo", length = 5, nullable = false)
    private String simbolo;
}
