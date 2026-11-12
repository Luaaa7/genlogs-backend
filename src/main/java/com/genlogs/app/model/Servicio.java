package com.genlogs.app.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import com.genlogs.app.enums.EstadoAprobacionServicio;

@Entity
@Table(name = "servicio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Servicio extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Long idServicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_servicio", nullable = false)
    private CategoriaServicio categoriaServicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_unidad_medida", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(name = "codigo_servicio", length = 30, nullable = false, unique = true)
    private String codigoServicio;

    @Column(name = "nombre_servicio", length = 200, nullable = false)
    private String nombreServicio;

    /** Horas estimadas para completar el servicio. NUMERIC(8,2), puede ser nula. */
    @Column(name = "duracion_estimada_horas", precision = 8, scale = 2)
    private BigDecimal duracionEstimadaHoras;

    /** Si el servicio se muestra en la web publica. Default TRUE en la BD. */
    @Column(name = "visible_web", nullable = false)
    @Builder.Default
    private Boolean visibleWeb = true;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    /** Costo del servicio. Nulo si todavía no se informó. */
    @Column(name = "costo", precision = 12, scale = 2)
    private BigDecimal costo;

    /** Porcentaje de margen. Nulo si no se informó: el servicio no queda pendiente. */
    @Column(name = "margen", precision = 5, scale = 2)
    private BigDecimal margen;

    /** Precio de venta. Calculado, salvo que precioManual sea true. */
    @Column(name = "precio_referencial", precision = 12, scale = 2)
    private BigDecimal precioReferencial;

    /** True: se guarda el precio enviado. False: se aplica costo × (1 + margen/100). */
    @Column(name = "precio_manual", nullable = false)
    @Builder.Default
    private Boolean precioManual = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_aprobacion", nullable = false, length = 30)
    @Builder.Default
    private EstadoAprobacionServicio estadoAprobacion = EstadoAprobacionServicio.APROBADO;
}
