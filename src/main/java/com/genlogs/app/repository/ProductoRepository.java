package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.genlogs.app.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    /** Catálogo de productos para el reporte (ordenado por nombre). */
    List<Producto> findByStatusOrderByNombreProductoAsc(String status);

    Optional<Producto> findByCodigoProducto(String codigoProducto);

    /**
     * Códigos que empiezan con el prefijo (activos e inactivos, porque la
     * columna es UNIQUE), del mayor al menor. Ordenar primero por longitud
     * hace que GLB-1000000 quede por encima de GLB-999999.
     */
    @Query("""
           SELECT p.codigoProducto FROM Producto p
           WHERE p.codigoProducto LIKE CONCAT(:prefijo, '%')
           ORDER BY LENGTH(p.codigoProducto) DESC, p.codigoProducto DESC
           """)
    List<String> buscarCodigosConPrefijo(String prefijo, Pageable pageable);

    /** Catálogo web: mismo orden global por código que el listado paginado. */
    List<Producto> findByStatusAndVisibleWeb(String status, Boolean visibleWeb, Sort sort);

    /** Muestra pública de la landing. El límite lo pone el Pageable. */
    List<Producto> findByStatusAndVisibleWeb(String status, Boolean visibleWeb, Pageable pageable);

    List<Producto> findByCategoriaProducto_IdCategoriaProductoAndStatus(Integer idCategoriaProducto, String status);

    @Query("""
           SELECT p FROM Producto p
           WHERE p.status = 'A'
             AND LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :texto, '%'))
           """)
    List<Producto> buscarPorNombre(String texto);
}
