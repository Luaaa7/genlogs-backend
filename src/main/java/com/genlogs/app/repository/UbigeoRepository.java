package com.genlogs.app.repository;

import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UbigeoRepository {

    interface DepartamentoRepository extends JpaRepository<Departamento, Integer> {
        List<Departamento> findByPais_IdPais(Integer idPais);
    }

    interface ProvinciaRepository extends JpaRepository<Provincia, Integer> {
        List<Provincia> findByDepartamento_IdDepartamento(Integer idDepartamento);
    }

    interface DistritoRepository extends JpaRepository<Distrito, Integer> {
        List<Distrito> findByProvincia_IdProvincia(Integer idProvincia);
        Optional<Distrito> findByUbigeo(String ubigeo);
    }
}