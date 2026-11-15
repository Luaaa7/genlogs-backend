package com.genlogs.app.fuentes.config;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Solo lo usa CredencialesFuenteService: el valor está cifrado y se descifra ahí. */
public interface FuenteCredencialRepository extends JpaRepository<FuenteCredencial, Long> {

    Optional<FuenteCredencial> findByFuente_CodigoAndTipo(CodigoFuente codigo, TipoCredencial tipo);

    boolean existsByFuente_CodigoAndTipo(CodigoFuente codigo, TipoCredencial tipo);
}
