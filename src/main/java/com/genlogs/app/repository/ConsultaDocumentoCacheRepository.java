package com.genlogs.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.ConsultaDocumentoCache;

public interface ConsultaDocumentoCacheRepository extends JpaRepository<ConsultaDocumentoCache, Long> {

    Optional<ConsultaDocumentoCache> findByNumeroDocumentoAndStatus(String numeroDocumento, String status);

    Optional<ConsultaDocumentoCache> findByNumeroDocumento(String numeroDocumento);
}
