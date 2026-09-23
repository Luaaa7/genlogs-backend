package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cotizacion_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class CotizacionDetalle extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cotizacion_detalle")
    private Long idCotizacionDetalle;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cotizacion", nullable = false)
    private Cotizacion cotizacion;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto")
    private Producto producto;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio")
    private Servicio servicio;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_unidad_medida", nullable = false)
    private UnidadMedida unidadMedida;
    
    @Column(name = "descripcion_personalizada", columnDefinition = "TEXT")
    private String descripcionPersonalizada;
    
    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;
    
    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 4)
    private BigDecimal precioUnitario;
    
    @Column(name = "descuento_unitario", nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;
    
    @Column(name = "importe_linea", nullable = false, precision = 16, scale = 2, columnDefinition = "NUMERIC(16,2) GENERATED ALWAYS AS (ROUND(cantidad * (precio_unitario - descuento_unitario), 2)) STORED")
    private BigDecimal importeLinea;
}
