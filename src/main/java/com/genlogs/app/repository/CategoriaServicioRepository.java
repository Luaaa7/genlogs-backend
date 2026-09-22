package com.genlogs.app.repository;

import com.genlogs.app.model.CategoriaServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaServicioRepository extends JpaRepository<CategoriaServicio, Integer> {

    List<CategoriaServicio> findByStatus(String status);

    boolean existsBySlugWebIgnoreCase(String slugWeb);
}
