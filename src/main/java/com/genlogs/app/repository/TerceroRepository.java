package com.genlogs.app.repository;

import com.genlogs.app.model.Tercero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerceroRepository extends JpaRepository<Tercero, Long> {

    Optional<Tercero> findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
            Integer idTipoDocumento, String numeroDocumento);

    boolean existsByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
            Integer idTipoDocumento, String numeroDocumento);

    Optional<Tercero> findByNumeroDocumento(String numeroDocumento);
}
