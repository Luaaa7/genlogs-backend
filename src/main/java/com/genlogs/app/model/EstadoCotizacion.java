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
@Table(name = "estado_cotizacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class EstadoCotizacion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_cotizacion")
    private Integer idEstadoCotizacion;

    @Column(name = "codigo_estado", nullable = false, unique = true, length = 20)
    private String codigoEstado;

    @Column(name = "nombre_estado", nullable = false, length = 60)
    private String nombreEstado;

    // DB: SMALLINT NOT NULL. Define el orden del flujo de estados
    // (BORRADOR=1, ENVIADA=2, EN_NEGOCIACION=3, APROBADA=4, ...).
    @Column(name = "orden_flujo", nullable = false)
    private Short ordenFlujo;

    @Column(name = "es_final", nullable = false)
    @Builder.Default
    private Boolean esFinal = false;
}
