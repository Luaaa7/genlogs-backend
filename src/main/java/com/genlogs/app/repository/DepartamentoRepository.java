package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Departamento;

public interface DepartamentoRepository extends JpaRepository<Departamento, Integer> {
    List<Departamento> findByPais_IdPais(Integer idPais);
}