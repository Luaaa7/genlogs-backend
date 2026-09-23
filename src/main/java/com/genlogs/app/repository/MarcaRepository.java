package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Marca;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {

    List<Marca> findByStatus(String status);

    List<Marca> findByPais_IdPaisAndStatus(Integer idPais, String status);
}
