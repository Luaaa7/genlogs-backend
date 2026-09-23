package com.genlogs.app.repository;

import com.genlogs.app.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    List<Servicio> findByStatus(String status);

    List<Servicio> findByCategoriaServicio_IdCategoriaServicio(Integer idCategoriaServicio);

    boolean existsByCodigoServicioIgnoreCase(String codigoServicio);
}
