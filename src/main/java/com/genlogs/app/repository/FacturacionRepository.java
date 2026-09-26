package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.Facturacion;

public interface FacturacionRepository extends JpaRepository<Facturacion, Long> {
    Optional<Facturacion> findBySerieComprobanteAndNumeroComprobante(String serie, String numero);
    
    @Query("SELECT f FROM Facturacion f WHERE f.ordenCompra.cotizacion.cliente.idCliente = :idCliente AND f.status = 'A' ORDER BY f.fechaEmision DESC")
    List<Facturacion> findByCliente(@Param("idCliente") Long idCliente);
        List<Facturacion> findByDateCreateBetween(java.time.LocalDateTime inicio, java.time.LocalDateTime fin);
}
