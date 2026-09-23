package com.genlogs.app.repository;

import com.genlogs.app.model.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonedaRepository extends JpaRepository<Moneda, Integer> {

    List<Moneda> findByStatus(String status);

    boolean existsByCodigoMonedaIgnoreCase(String codigoMoneda);
}
