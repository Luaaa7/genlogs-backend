package com.genlogs.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.TipoComprobante;

public interface TipoComprobanteRepository extends JpaRepository<TipoComprobante, Integer> {
    Optional<TipoComprobante> findByCodigoTipo(String codigoTipo);
}
