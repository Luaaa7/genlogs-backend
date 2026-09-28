package com.genlogs.app.repository;

import com.genlogs.app.model.Departamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartamentoRepository extends JpaRepository<Departamento, Integer> {
    List<Departamento> findByPais_IdPais(Integer idPais);
}
