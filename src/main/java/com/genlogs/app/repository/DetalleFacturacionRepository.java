package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.DetalleFacturacion;

public interface DetalleFacturacionRepository extends JpaRepository<DetalleFacturacion, Long> {
    @Query("SELECT df FROM DetalleFacturacion df WHERE df.facturacion.idFacturacion = :idFacturacion AND df.status = 'A'")
    List<DetalleFacturacion> findByFacturacion(@Param("idFacturacion") Long idFacturacion);
}
