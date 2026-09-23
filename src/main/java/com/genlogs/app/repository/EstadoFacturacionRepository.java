package com.genlogs.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.EstadoFacturacion;

public interface EstadoFacturacionRepository extends JpaRepository<EstadoFacturacion, Integer> {
    Optional<EstadoFacturacion> findByCodigoEstado(String codigoEstado);
}
