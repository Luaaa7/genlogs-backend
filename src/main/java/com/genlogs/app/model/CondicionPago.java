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
@Table(name = "condicion_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class CondicionPago extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicion_pago")
    private Integer idCondicionPago;

    @Column(name = "nombre_condicion", length = 100, nullable = false, unique = true)
    private String nombreCondicion;

    /** DB: SMALLINT NOT NULL DEFAULT 0 (0 = contado). CHECK dias_credito >= 0. */
    @Column(name = "dias_credito", nullable = false)
    private Short diasCredito;
}
