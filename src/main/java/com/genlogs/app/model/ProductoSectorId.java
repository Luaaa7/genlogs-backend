package com.genlogs.app.model;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Clave compuesta (id_producto, id_sector_economico) de producto_sector. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductoSectorId implements Serializable {
    private Long idProducto;
    private Integer idSectorEconomico;
}
