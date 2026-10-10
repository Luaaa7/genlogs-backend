package com.genlogs.app.repository;

import com.genlogs.app.model.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinciaRepository extends JpaRepository<Provincia, Integer> {
    List<Provincia> findByDepartamento_IdDepartamento(Integer idDepartamento);
}
