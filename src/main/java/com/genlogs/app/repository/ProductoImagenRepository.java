package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.ProductoImagen;

public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Long> {

    List<ProductoImagen> findByProducto_IdProductoAndStatus(Long idProducto, String status);

    Optional<ProductoImagen> findByProducto_IdProductoAndEsPrincipalTrueAndStatus(Long idProducto, String status);
}
