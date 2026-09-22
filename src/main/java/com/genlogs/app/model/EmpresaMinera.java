package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Ficha de la unidad minera: 1 por cliente que además es empresa minera. */
@Entity
@Table(name = "empresa_minera")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class EmpresaMinera extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empresa_minera")
    private Long idEmpresaMinera;

    /** id_cliente es UNIQUE en la BD -> relación 1 a 1 con Cliente (de Ale). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false, unique = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_etapa_comercial", nullable = false)
    private EtapaComercialMinera etapaComercial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_operacion", nullable = false)
    private TipoOperacionMinera tipoOperacion;

    @Column(name = "nombre_unidad_minera", length = 150, nullable = false)
    private String nombreUnidadMinera;

    /** DB: NUMERIC(9,6) NOT NULL, CHECK entre -19.5 y 0.5 (territorio peruano). */
    @Column(name = "latitud", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitud;

    /** DB: NUMERIC(9,6) NOT NULL, CHECK entre -82.0 y -68.0 (territorio peruano). */
    @Column(name = "longitud", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitud;

    @Column(name = "altitud_msnm")
    private Integer altitudMsnm;

    /** Texto libre, ej: "5,000 TMD". */
    @Column(name = "capacidad_produccion", length = 100)
    private String capacidadProduccion;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;
}
