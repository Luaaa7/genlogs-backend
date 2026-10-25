package com.genlogs.app.repository;

import com.genlogs.app.model.EmpresaMinera;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmpresaMineraRepository extends JpaRepository<EmpresaMinera, Long> {

    /** Trae en la misma consulta el cliente (con su tercero), la etapa y el tipo de operación. */
    @EntityGraph(attributePaths = {"cliente", "cliente.tercero", "etapaComercial", "tipoOperacion"})
    List<EmpresaMinera> findByStatus(String status);

    Optional<EmpresaMinera> findByCliente_IdCliente(Long idCliente);

    boolean existsByCliente_IdCliente(Long idCliente);
}