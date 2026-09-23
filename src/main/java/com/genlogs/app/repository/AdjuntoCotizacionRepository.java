package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.AdjuntoCotizacion;

public interface AdjuntoCotizacionRepository extends JpaRepository<AdjuntoCotizacion, Long> {
    @Query("SELECT a FROM AdjuntoCotizacion a WHERE a.cotizacion.idCotizacion = :idCotizacion AND a.status = 'A'")
    List<AdjuntoCotizacion> findByCotizacion(@Param("idCotizacion") Long idCotizacion);
}
