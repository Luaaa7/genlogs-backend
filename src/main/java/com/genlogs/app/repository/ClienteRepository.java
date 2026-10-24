package com.genlogs.app.repository;

import com.genlogs.app.model.Cliente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTercero_IdTercero(Long idTercero);

    List<Cliente> findBySituacionAndStatus(String situacion, String status);

    /**
     * Clientes activos cuya razón social o número de documento contiene el patrón
     * (ya en minúsculas y con % a los lados; "%" = sin filtro). Ordenados por razón social.
     */
    @Query("""
           SELECT c FROM Cliente c JOIN c.tercero t
           WHERE c.situacion = 'ACTIVO' AND c.status = 'A'
             AND (LOWER(t.razonSocial) LIKE :patron OR t.numeroDocumento LIKE :patron)
           ORDER BY t.razonSocial
           """)
    List<Cliente> buscarActivos(@Param("patron") String patron, Pageable pageable);

    // --- Soporte para DashboardController ---
    long countBySituacionAndStatus(String situacion, String status);

    @Query("""
           SELECT c FROM Cliente c
           WHERE c.tercero.numeroDocumento = :numeroDocumento
             AND c.status = 'A'
           """)
    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);
}
