package com.genlogs.app.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.genlogs.app.enums.FuenteTipoCambio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tipo de cambio de un dia para una moneda (RF-86): cuantos soles vale 1 unidad
 * de la moneda (valor de venta, D-19). Uno por fecha y moneda.
 * Viene del Paso 0 (tabla en v3_2.sql). La lectura es con
 * TipoCambioService.delDia(); la consulta diaria y la correccion manual son de BE-D.
 */
@Entity
@Table(name = "tipo_cambio",
        uniqueConstraints = @UniqueConstraint(name = "uq_tipo_cambio_fecha", columnNames = {"fecha", "id_moneda"}))
@Getter
@Setter
@NoArgsConstructor
public class TipoCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_cambio")
    private Long idTipoCambio;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_moneda", nullable = false)
    private Moneda moneda;

    @Column(name = "valor", nullable = false, precision = 10, scale = 4)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuente", nullable = false, length = 10)
    private FuenteTipoCambio fuente;

    /** id_usuario de quien lo corrigio a mano (NULL si lo trajo el sistema). */
    @Column(name = "editado_por")
    private Long editadoPor;
}