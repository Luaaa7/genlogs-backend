package com.genlogs.app.fuentes.prueba;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuentePruebaRepository extends JpaRepository<FuentePrueba, Long> {

    /** Número de la última corrida de la tienda; 0 si nunca se probó. */
    @Query("select coalesce(max(p.corrida), 0) from FuentePrueba p where p.fuente.idFuente = :idFuente")
    int ultimaCorrida(@Param("idFuente") Integer idFuente);

    List<FuentePrueba> findByFuente_IdFuenteAndCorridaAndStatusOrderByIdPrueba(Integer idFuente, Integer corrida,
                                                                                String status);
}