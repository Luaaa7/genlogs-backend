package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.ProductoProveedor;
import com.genlogs.app.model.ProductoProveedorId;

public interface ProductoProveedorRepository extends JpaRepository<ProductoProveedor, ProductoProveedorId> {

    List<ProductoProveedor> findByProducto_IdProductoAndStatus(Long idProducto, String status);

    List<ProductoProveedor> findByProveedor_IdProveedorAndStatus(Long idProveedor, String status);
}
