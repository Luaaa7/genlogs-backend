package com.genlogs.app.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Feriado;

public interface FeriadoRepository extends JpaRepository<Feriado, Integer> {

    boolean existsByFechaAndStatus(LocalDate fecha, String status);

    List<Feriado> findByStatusAndFechaGreaterThanEqualOrderByFechaAsc(String status, LocalDate desde);
}
