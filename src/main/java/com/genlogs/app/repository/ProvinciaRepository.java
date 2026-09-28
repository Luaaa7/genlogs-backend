package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Provincia;

public interface ProvinciaRepository extends JpaRepository<Provincia, Integer> {
    List<Provincia> findByDepartamento_IdDepartamento(Integer idDepartamento);
}