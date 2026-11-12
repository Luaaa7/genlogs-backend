package com.genlogs.app.model;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.enums.TipoRepuesto;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Producto del catálogo B2B (manufactura, minería e industrial a demanda).
 * id_marca es opcional: NULL para productos sin marca (p. ej. de
 * fabricación a medida).
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Producto extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long idProducto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_categoria_producto", nullable = false)
    private CategoriaProducto categoriaProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_marca")
    private Marca marca;

    // [PENDIENTE] UnidadMedida es un catálogo general (Módulo 3 -
    // Catálogos Generales, aún sin asignar en el tablero). Si al
    // compilar todavía no existe com.genlogs.app.model.UnidadMedida,
    // coordina con el equipo para no bloquear tu rama.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_unidad_medida", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(name = "codigo_producto", nullable = false, unique = true, length = 30)
    private String codigoProducto;

    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;

    @Column(name = "procedencia", length = 100)
    private String procedencia;

    @Column(name = "visible_web", nullable = false)
    @Builder.Default
    private Boolean visibleWeb = true;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    // ---- v3.2 · Vitrina (BE-E, columnas de v3_2.sql) ----

    /** N.º de parte del fabricante (D-01). */
    @Column(name = "numero_parte", length = 60)
    private String numeroParte;

    /** "Original (OEM)" o "Alternativo compatible" (RF-83). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_repuesto", nullable = false, length = 12)
    @Builder.Default
    private TipoRepuesto tipoRepuesto = TipoRepuesto.OEM;

    /** Nacional (24-48 h) o importado (5-7 días hábiles). */
    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 10)
    @Builder.Default
    private OrigenRepuesto origen = OrigenRepuesto.NACIONAL;

    /** Los productos nuevos nacen como BORRADOR; al público solo llega PUBLICADO. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_publicacion", nullable = false, length = 10)
    @Builder.Default
    private EstadoPublicacion estadoPublicacion = EstadoPublicacion.BORRADOR;

    /** Precio referencial de GENLOGS en soles (costo puesto en Lima + margen de repuestos, D-17). */
    @Column(name = "precio_referencial", precision = 12, scale = 2)
    private BigDecimal precioReferencial;

    @Column(name = "fecha_ultima_verificacion")
    private LocalDateTime fechaUltimaVerificacion;

    /** Cotizaciones en las que no se pudo conseguir; con OCULTAR_FALLAS se oculta (RF-83). */
    @Column(name = "fallas_cotizacion", nullable = false)
    @Builder.Default
    private Short fallasCotizacion = 0;

    /** Las columnas NOT NULL de la v3.2 nunca se envían vacías (también si se crea con new Producto()). */
    @PrePersist
    void valoresV32PorDefecto() {
        if (tipoRepuesto == null) tipoRepuesto = TipoRepuesto.OEM;
        if (origen == null) origen = OrigenRepuesto.NACIONAL;
        if (estadoPublicacion == null) estadoPublicacion = EstadoPublicacion.BORRADOR;
        if (fallasCotizacion == null) fallasCotizacion = 0;
    }
}
