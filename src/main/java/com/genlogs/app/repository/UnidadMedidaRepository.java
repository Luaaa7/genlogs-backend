package com.genlogs.app.repository;

import com.genlogs.app.model.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {

    List<UnidadMedida> findByStatus(String status);

    boolean existsByCodigoUnidadIgnoreCase(String codigoUnidad);
}
