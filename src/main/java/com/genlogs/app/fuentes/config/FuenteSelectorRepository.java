package com.genlogs.app.fuentes.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FuenteSelectorRepository extends JpaRepository<FuenteSelector, Long> {

    /** Las reglas activas de una tienda, para enviarlas al servicio de lectura (A4). */
    List<FuenteSelector> findByFuente_CodigoAndStatus(CodigoFuente codigo, String status);
}
