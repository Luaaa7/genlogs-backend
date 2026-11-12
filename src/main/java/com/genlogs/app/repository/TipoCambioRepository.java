package com.genlogs.app.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.genlogs.app.model.TipoCambio;

@Repository
public interface TipoCambioRepository extends JpaRepository<TipoCambio, Long> {

    /** El tipo de cambio de esa fecha o, si no hay, el ultimo anterior. */
    Optional<TipoCambio> findFirstByMoneda_CodigoMonedaAndFechaLessThanEqualOrderByFechaDesc(
            String codigoMoneda, LocalDate fecha);

    /** La fila exacta de ese día, si existe. */
    Optional<TipoCambio> findByMoneda_CodigoMonedaAndFecha(String codigoMoneda, LocalDate fecha);
}