package com.genlogs.app.repository;

import com.genlogs.app.model.CondicionPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CondicionPagoRepository extends JpaRepository<CondicionPago, Integer> {

    List<CondicionPago> findByStatus(String status);

    boolean existsByNombreCondicionIgnoreCase(String nombreCondicion);
}
