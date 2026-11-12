package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Despacho;

public interface DespachoRepository extends JpaRepository<Despacho, Long> {

    boolean existsByOrdenCompra_IdOrdenCompraAndStatus(Long idOrdenCompra, String status);

    List<Despacho> findByStatusOrderByIdDespachoDesc(String status);

    List<Despacho> findByCotizacion_Cliente_IdClienteAndStatusOrderByIdDespachoDesc(Long idCliente, String status);
}
