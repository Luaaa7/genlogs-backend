package com.genlogs.app.fuentes.config;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FuenteBusquedaConfigRepository extends JpaRepository<FuenteBusquedaConfig, Integer> {

    Optional<FuenteBusquedaConfig> findByCodigo(CodigoFuente codigo);

    /** Las que entran en la búsqueda (N1) y en la verificación de la vitrina (R1). */
    List<FuenteBusquedaConfig> findByActivaTrueAndStatusOrderByIdFuente(String status);
}
