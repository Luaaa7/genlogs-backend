package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.SeguimientoCotizacion;

public interface SeguimientoCotizacionRepository extends JpaRepository<SeguimientoCotizacion, Long> {
    @Query("SELECT s FROM SeguimientoCotizacion s WHERE s.cotizacion.idCotizacion = :idCotizacion AND s.status = 'A' ORDER BY s.fechaEvento DESC")
    List<SeguimientoCotizacion> findByCotizacion(@Param("idCotizacion") Long idCotizacion);
}
