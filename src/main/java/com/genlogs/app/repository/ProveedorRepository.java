package com.genlogs.app.repository;

import com.genlogs.app.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Optional<Proveedor> findByTercero_IdTercero(Long idTercero);

    List<Proveedor> findBySituacionAndStatus(String situacion, String status);
}
