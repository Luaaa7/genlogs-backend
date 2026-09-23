package com.genlogs.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.EstadoOrdenCompra;

public interface EstadoOrdenCompraRepository extends JpaRepository<EstadoOrdenCompra, Integer> {
    Optional<EstadoOrdenCompra> findByCodigoEstado(String codigoEstado);
}
