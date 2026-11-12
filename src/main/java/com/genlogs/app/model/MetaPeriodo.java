package com.genlogs.app.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Meta de cotizaciones dentro del plazo para un año y un mes. */
@Entity
@Table(name = "meta_periodo")
@IdClass(MetaPeriodoId.class)
@Getter
@Setter
@NoArgsConstructor
public class MetaPeriodo {

    @Id
    @Column(name = "anio", nullable = false)
    private Short anio;

    @Id
    @Column(name = "mes", nullable = false)
    private Short mes;

    @Column(name = "meta_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal metaPorcentaje = new BigDecimal("90.00");
}
