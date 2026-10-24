package com.genlogs.app.repository;

import com.genlogs.app.model.Mineral;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MineralRepository extends JpaRepository<Mineral, Integer> {

    List<Mineral> findByStatus(String status);

    boolean existsByNombreMineralIgnoreCase(String nombreMineral);
}
