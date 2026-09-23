package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.CotizacionDetalle;

public interface CotizacionDetalleRepository extends JpaRepository<CotizacionDetalle, Long> {
    @Query("SELECT cd FROM CotizacionDetalle cd WHERE cd.cotizacion.idCotizacion = :idCotizacion AND cd.status = 'A'")
    List<CotizacionDetalle> findByCotizacion(@Param("idCotizacion") Long idCotizacion);
}
