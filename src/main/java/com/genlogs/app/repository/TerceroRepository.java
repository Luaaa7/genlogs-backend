package com.genlogs.app.repository;

import com.genlogs.app.model.Tercero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TerceroRepository extends JpaRepository<Tercero, Long> {

    Optional<Tercero> findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
            Integer idTipoDocumento, String numeroDocumento);

    boolean existsByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
            Integer idTipoDocumento, String numeroDocumento);

    Optional<Tercero> findByNumeroDocumento(String numeroDocumento);

    /**
     * Cliente o proveedor ya registrado con ese RUC o DNI, con su distrito
     * cargado (lo usa la consulta de documentos antes de ir a Factiliza).
     */
    @Query("""
           SELECT t FROM Tercero t
           JOIN FETCH t.tipoDocumento td
           JOIN FETCH t.distrito
           WHERE td.codigoTipo = :codigoTipo AND t.numeroDocumento = :numeroDocumento
           """)
    Optional<Tercero> buscarRegistrado(@Param("codigoTipo") String codigoTipo,
                                       @Param("numeroDocumento") String numeroDocumento);
}