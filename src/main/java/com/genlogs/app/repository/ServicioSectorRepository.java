package com.genlogs.app.repository;

import com.genlogs.app.model.ServicioSector;
import com.genlogs.app.model.ServicioSectorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicioSectorRepository extends JpaRepository<ServicioSector, ServicioSectorId> {

    List<ServicioSector> findByServicio_IdServicio(Long idServicio);
}
