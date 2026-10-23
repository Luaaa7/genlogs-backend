package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.genlogs.app.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    /** Catálogo de productos para el reporte (ordenado por nombre). */
    List<Producto> findByStatusOrderByNombreProductoAsc(String status);

    Optional<Producto> findByCodigoProducto(String codigoProducto);

    /** Catálogo web: mismo orden global por código que el listado paginado. */
    List<Producto> findByStatusAndVisibleWeb(String status, Boolean visibleWeb, Sort sort);

    List<Producto> findByCategoriaProducto_IdCategoriaProductoAndStatus(Integer idCategoriaProducto, String status);

    @Query("""
           SELECT p FROM Producto p
           WHERE p.status = 'A'
             AND LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :texto, '%'))
           """)
    List<Producto> buscarPorNombre(String texto);
}
