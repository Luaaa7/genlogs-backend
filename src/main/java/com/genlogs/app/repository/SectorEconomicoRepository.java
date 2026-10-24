package com.genlogs.app.repository;

import com.genlogs.app.model.SectorEconomico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SectorEconomicoRepository extends JpaRepository<SectorEconomico, Integer> {

    List<SectorEconomico> findByStatus(String status);

    boolean existsByNombreSectorIgnoreCase(String nombreSector);
}
