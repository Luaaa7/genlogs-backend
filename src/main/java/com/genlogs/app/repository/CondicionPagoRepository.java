package com.genlogs.app.repository;

import com.genlogs.app.model.CondicionPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CondicionPagoRepository extends JpaRepository<CondicionPago, Integer> {

    List<CondicionPago> findByStatus(String status);

    Optional<CondicionPago> findByNombreCondicion(String nombreCondicion);

    /** Búsqueda tolerante a mayúsculas/espacios (el frontend manda "CREDITO_30"). */
    Optional<CondicionPago> findFirstByNombreCondicionIgnoreCase(String nombreCondicion);

    /**
     * Busca por días de crédito: es la única forma de resolver un código del
     * frontend ("CREDITO_30" -> 30 días) cuando el nombre guardado en la BD
     * está escrito de otra forma ("Crédito 30 días").
     */
    Optional<CondicionPago> findFirstByDiasCreditoAndStatusOrderByIdCondicionPagoAsc(Short diasCredito, String status);

    boolean existsByNombreCondicionIgnoreCase(String nombreCondicion);
}
