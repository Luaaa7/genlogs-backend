package com.genlogs.app.repository;

import com.genlogs.app.model.TasaIgv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TasaIgvRepository extends JpaRepository<TasaIgv, Integer> {

    /** La tasa que rige en una fecha: la de "vigente_desde" más reciente que no la pase. */
    Optional<TasaIgv> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);

    List<TasaIgv> findAllByOrderByVigenteDesdeDesc();

    boolean existsByVigenteDesde(LocalDate vigenteDesde);
}