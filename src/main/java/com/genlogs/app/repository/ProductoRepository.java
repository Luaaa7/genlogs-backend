package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.genlogs.app.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findByCodigoProducto(String codigoProducto);

    List<Producto> findByStatusAndVisibleWeb(String status, Boolean visibleWeb);

    List<Producto> findByCategoriaProducto_IdCategoriaProductoAndStatus(Integer idCategoriaProducto, String status);

    @Query("""
           SELECT p FROM Producto p
           WHERE p.status = 'A'
             AND LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :texto, '%'))
           """)
    List<Producto> buscarPorNombre(String texto);
        List<Producto> findByDateCreateBetween(java.time.LocalDateTime inicio, java.time.LocalDateTime fin);
}
