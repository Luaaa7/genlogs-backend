package com.genlogs.app.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Llave compuesta de servicio_sector. La tabla NO tiene un id propio:
 * su primary key es (id_servicio, id_sector_economico).
 *
 * IMPORTANTE: los nombres de estos campos deben coincidir EXACTO con
 * los nombres de los campos @Id de ServicioSector.java (servicio,
 * sectorEconomico), y sus tipos deben coincidir con el tipo del ID
 * de la entidad referenciada (Servicio -> Long, SectorEconomico -> Integer).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ServicioSectorId implements Serializable {

    private Long servicio;
    private Integer sectorEconomico;
}
