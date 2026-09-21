package com.genlogs.app.repository;

import com.genlogs.app.model.Pais;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaisRepository extends JpaRepository<Pais, Integer> {
    Optional<Pais> findByCodigoIso(String codigoIso);
}
