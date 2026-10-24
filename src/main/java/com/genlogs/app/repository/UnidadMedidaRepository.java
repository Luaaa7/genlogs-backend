package com.genlogs.app.repository;

import com.genlogs.app.model.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {

    List<UnidadMedida> findByStatus(String status);

    /** Unidad por defecto (la más antigua activa): se usa cuando la línea no indica idUnidadMedida. */
    Optional<UnidadMedida> findFirstByStatusOrderByIdUnidadMedidaAsc(String status);

    boolean existsByCodigoUnidadIgnoreCase(String codigoUnidad);
}
