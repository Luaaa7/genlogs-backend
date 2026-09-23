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
@Table(name = "sector_economico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class SectorEconomico extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sector_economico")
    private Integer idSectorEconomico;

    // Nombre exacto porque ClienteService.java (de Ale) ya llama a
    // getSectorEconomico().getNombreSector()
    @Column(name = "nombre_sector", length = 100, nullable = false, unique = true)
    private String nombreSector;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}
