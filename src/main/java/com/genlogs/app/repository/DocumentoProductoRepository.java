package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.DocumentoProducto;

public interface DocumentoProductoRepository extends JpaRepository<DocumentoProducto, Long> {

    List<DocumentoProducto> findByProducto_IdProductoAndStatus(Long idProducto, String status);
}
