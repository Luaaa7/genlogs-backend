package com.genlogs.app.repository;

import com.genlogs.app.model.Distrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DistritoRepository extends JpaRepository<Distrito, Integer> {
    List<Distrito> findByProvincia_IdProvincia(Integer idProvincia);
    Optional<Distrito> findByUbigeo(String ubigeo);
}
