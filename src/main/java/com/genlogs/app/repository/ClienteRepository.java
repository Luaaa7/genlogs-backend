package com.genlogs.app.repository;

import com.genlogs.app.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTercero_IdTercero(Long idTercero);

    List<Cliente> findBySituacionAndStatus(String situacion, String status);

    @Query("""
           SELECT c FROM Cliente c
           WHERE c.tercero.numeroDocumento = :numeroDocumento
             AND c.status = 'A'
           """)
    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);
}
