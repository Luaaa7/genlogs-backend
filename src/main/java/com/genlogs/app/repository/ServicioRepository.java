package com.genlogs.app.repository;

import com.genlogs.app.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    List<Servicio> findByStatus(String status);

    /** Catálogo de servicios para el reporte (ordenado por nombre). */
    List<Servicio> findByStatusOrderByNombreServicioAsc(String status);

    List<Servicio> findByCategoriaServicio_IdCategoriaServicio(Integer idCategoriaServicio);

    boolean existsByCodigoServicioIgnoreCase(String codigoServicio);

    List<Servicio> findByDateCreateBetween(java.time.LocalDateTime inicio, java.time.LocalDateTime fin);
}