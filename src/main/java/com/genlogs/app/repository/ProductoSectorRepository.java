package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.ProductoSector;
import com.genlogs.app.model.ProductoSectorId;

public interface ProductoSectorRepository extends JpaRepository<ProductoSector, ProductoSectorId> {

    List<ProductoSector> findByProducto_IdProductoAndStatus(Long idProducto, String status);

    Optional<ProductoSector> findByProducto_IdProductoAndSectorEconomico_IdSectorEconomico(
            Long idProducto, Integer idSectorEconomico);
}
