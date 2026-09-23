package com.genlogs.app.repository;

import com.genlogs.app.model.EstadoCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoCotizacionRepository extends JpaRepository<EstadoCotizacion, Integer> {
    Optional<EstadoCotizacion> findByCodigoEstado(String codigoEstado);
}
