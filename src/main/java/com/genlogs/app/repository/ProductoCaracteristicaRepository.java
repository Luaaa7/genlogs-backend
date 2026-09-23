package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.ProductoCaracteristica;
import com.genlogs.app.model.ProductoCaracteristicaId;

public interface ProductoCaracteristicaRepository extends JpaRepository<ProductoCaracteristica, ProductoCaracteristicaId> {

    List<ProductoCaracteristica> findByProducto_IdProductoAndStatus(Long idProducto, String status);

    Optional<ProductoCaracteristica> findByProducto_IdProductoAndCaracteristica_IdCaracteristica(
            Long idProducto, Integer idCaracteristica);
}
