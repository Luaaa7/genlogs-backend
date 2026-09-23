package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Sectores económicos a los que aplica un producto, para los filtros
 * del catálogo web (PK compuesta). id_sector_economico referencia el
 * catálogo SectorEconomico del Módulo 3 (Catálogos Generales).
 * [PENDIENTE] SectorEconomico la crea Mell: si al compilar todavía
 * no existe en com.genlogs.app.model, coordina con ella.
 */
@Entity
@Table(name = "producto_sector")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ProductoSector extends Auditable {

    @EmbeddedId
    private ProductoSectorId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idProducto")
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("idSectorEconomico")
    @JoinColumn(name = "id_sector_economico", nullable = false)
    private SectorEconomico sectorEconomico;
}
