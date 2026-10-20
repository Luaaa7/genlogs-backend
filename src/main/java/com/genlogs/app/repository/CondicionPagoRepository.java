package com.genlogs.app.repository;

import com.genlogs.app.model.CondicionPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CondicionPagoRepository extends JpaRepository<CondicionPago, Integer> {

    List<CondicionPago> findByStatus(String status);

    boolean existsByNombreCondicionIgnoreCase(String nombreCondicion);
}
