package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "detalle_facturacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class DetalleFacturacion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_facturacion")
    private Long idDetalleFacturacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_facturacion", nullable = false)
    private Facturacion facturacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio")
    private Servicio servicio;

    @Column(name = "descripcion_personalizada", columnDefinition = "TEXT")
    private String descripcionPersonalizada;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 4)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_unitario", nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;

    // Columna GENERATED ALWAYS ... STORED en la BD: Hibernate NO debe intentar
    // insertarla/actualizarla (mismo caso que CotizacionDetalle.importeLinea en Fase 3).
    @Column(name = "importe_linea", insertable = false, updatable = false)
    private BigDecimal importeLinea;
}
