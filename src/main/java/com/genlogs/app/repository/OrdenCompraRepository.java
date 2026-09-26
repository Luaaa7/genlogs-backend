package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.OrdenCompra;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {
    Optional<OrdenCompra> findByNumeroOrdenCompra(String numeroOrdenCompra);
    
    @Query("SELECT oc FROM OrdenCompra oc WHERE oc.cotizacion.cliente.idCliente = :idCliente AND oc.status = 'A' ORDER BY oc.fechaRecepcion DESC")
    List<OrdenCompra> findByCliente(@Param("idCliente") Long idCliente);
        List<OrdenCompra> findByDateCreateBetween(java.time.LocalDateTime inicio, java.time.LocalDateTime fin);
}
