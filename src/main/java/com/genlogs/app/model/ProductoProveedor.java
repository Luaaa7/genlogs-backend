package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Proveedores que ofrecen un producto, con precio referencial y
 * tiempo de entrega (PK compuesta). id_moneda es opcional, pero es
 * obligatorio si se informa precio_referencial (regla de negocio
 * ck_producto_proveedor_moneda validada también en el service).
 * [PENDIENTE] Moneda es un catálogo general (Módulo 3, aún sin
 * asignar en el tablero): coordina con el equipo si aún no existe.
 */
@Entity
@Table(name = "producto_proveedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ProductoProveedor extends Auditable {

    @EmbeddedId
    private ProductoProveedorId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idProducto")
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idProveedor")
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @Column(name = "precio_referencial", precision = 14, scale = 4)
    private BigDecimal precioReferencial;

    @Column(name = "tiempo_entrega_dias")
    private Integer tiempoEntregaDias;

    @Column(name = "proveedor_preferido", nullable = false)
    @Builder.Default
    private Boolean proveedorPreferido = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_moneda")
    private Moneda moneda;
}
