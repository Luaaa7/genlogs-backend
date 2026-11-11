package com.genlogs.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.genlogs.app.model.ProductoImagen;

public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Long> {

    List<ProductoImagen> findByProducto_IdProductoAndStatus(Long idProducto, String status);

    Optional<ProductoImagen> findByProducto_IdProductoAndEsPrincipalTrueAndStatus(Long idProducto, String status);

    @Query("""
           SELECT i FROM ProductoImagen i
           WHERE i.status = 'A'
             AND i.esPrincipal = true
             AND i.producto.idProducto IN :ids
           """)
    List<ProductoImagen> buscarPrincipalesActivas(Collection<Long> ids);
}
