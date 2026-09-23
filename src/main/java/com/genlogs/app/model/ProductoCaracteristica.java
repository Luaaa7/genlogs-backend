package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/** Valor de una característica técnica para la ficha de un producto (PK compuesta). */
@Entity
@Table(name = "producto_caracteristica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ProductoCaracteristica extends Auditable {

    @EmbeddedId
    private ProductoCaracteristicaId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idProducto")
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idCaracteristica")
    @JoinColumn(name = "id_caracteristica", nullable = false)
    private CaracteristicaTecnica caracteristica;

    @Column(name = "valor_caracteristica", nullable = false, length = 255)
    private String valorCaracteristica;
}
