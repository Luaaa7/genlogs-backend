package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.SeguimientoOrdenCompra;

public interface SeguimientoOrdenCompraRepository extends JpaRepository<SeguimientoOrdenCompra, Long> {

    @Query("SELECT s FROM SeguimientoOrdenCompra s WHERE s.ordenCompra.idOrdenCompra = :idOrdenCompra AND s.status = 'A' ORDER BY s.fechaEvento DESC, s.idSeguimiento DESC")
    List<SeguimientoOrdenCompra> findByOrdenCompra(@Param("idOrdenCompra") Long idOrdenCompra);
}