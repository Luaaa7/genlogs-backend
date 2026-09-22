package com.genlogs.app.repository;

import com.genlogs.app.model.Mineral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MineralRepository extends JpaRepository<Mineral, Integer> {

    List<Mineral> findByStatus(String status);

    boolean existsByNombreMineralIgnoreCase(String nombreMineral);
}
