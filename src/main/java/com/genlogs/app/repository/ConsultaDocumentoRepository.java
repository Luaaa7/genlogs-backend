package com.genlogs.app.repository;

import com.genlogs.app.model.ConsultaDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultaDocumentoRepository extends JpaRepository<ConsultaDocumento, Long> {

    Optional<ConsultaDocumento> findByNumeroDocumento(String numeroDocumento);
}