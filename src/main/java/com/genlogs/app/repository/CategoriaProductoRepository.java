package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.CategoriaProducto;

public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Integer> {

    List<CategoriaProducto> findByStatus(String status);

    Optional<CategoriaProducto> findBySlugWeb(String slugWeb);

    List<CategoriaProducto> findByCategoriaPadreIsNullAndStatus(String status);

    List<CategoriaProducto> findByCategoriaPadre_IdCategoriaProductoAndStatus(Integer idCategoriaPadre, String status);
}
