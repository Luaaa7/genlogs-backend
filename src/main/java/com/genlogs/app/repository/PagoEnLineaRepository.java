package com.genlogs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.PagoEnLinea;

public interface PagoEnLineaRepository extends JpaRepository<PagoEnLinea, Long> {

    boolean existsByProveedorAndIdPagoExterno(String proveedor, String idPagoExterno);
}