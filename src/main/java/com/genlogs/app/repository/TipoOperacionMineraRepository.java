package com.genlogs.app.repository;

import com.genlogs.app.model.TipoOperacionMinera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipoOperacionMineraRepository extends JpaRepository<TipoOperacionMinera, Integer> {

    List<TipoOperacionMinera> findByStatus(String status);

    boolean existsByNombreOperacionIgnoreCase(String nombreOperacion);
}
