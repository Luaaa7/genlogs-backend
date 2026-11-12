package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.EstadoReclamo;
import com.genlogs.app.model.PrioridadReclamo;
import com.genlogs.app.model.Reclamo;
import com.genlogs.app.model.TipoReclamo;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    @Query("SELECT r.codigoReclamo FROM Reclamo r WHERE r.tipo = :tipo")
    List<String> codigosPorTipo(@Param("tipo") TipoReclamo tipo);

    boolean existsByCodigoReclamo(String codigoReclamo);

    /** Portal (RF-70): solo los reclamos de la empresa del cliente. */
    List<Reclamo> findByCliente_IdClienteAndStatusOrderByFechaRegistroDescIdReclamoDesc(Long idCliente, String status);

    Optional<Reclamo> findByIdReclamoAndCliente_IdClienteAndStatus(Long idReclamo, Long idCliente, String status);

    // Listado paginado con filtros opcionales (null = sin filtro).
    @Query(value = "SELECT r FROM Reclamo r "
            + "LEFT JOIN r.cliente c LEFT JOIN r.responsable u "
            + "WHERE r.status = 'A' "
            + "AND (:tipo IS NULL OR r.tipo = :tipo) "
            + "AND (:estado IS NULL OR r.estado = :estado) "
            + "AND (:prioridad IS NULL OR r.prioridad = :prioridad) "
            + "AND (:idCliente IS NULL OR c.idCliente = :idCliente) "
            + "AND (:idResponsable IS NULL OR u.idUsuario = :idResponsable) "
            + "ORDER BY r.fechaRegistro DESC, r.idReclamo DESC",
            countQuery = "SELECT COUNT(r) FROM Reclamo r "
            + "LEFT JOIN r.cliente c LEFT JOIN r.responsable u "
            + "WHERE r.status = 'A' "
            + "AND (:tipo IS NULL OR r.tipo = :tipo) "
            + "AND (:estado IS NULL OR r.estado = :estado) "
            + "AND (:prioridad IS NULL OR r.prioridad = :prioridad) "
            + "AND (:idCliente IS NULL OR c.idCliente = :idCliente) "
            + "AND (:idResponsable IS NULL OR u.idUsuario = :idResponsable)")
    Page<Reclamo> buscar(@Param("tipo") TipoReclamo tipo,
                         @Param("estado") EstadoReclamo estado,
                         @Param("prioridad") PrioridadReclamo prioridad,
                         @Param("idCliente") Long idCliente,
                         @Param("idResponsable") Long idResponsable,
                         Pageable pageable);

    /** Para el dashboard: reclamos abiertos (no finales). */
    @Query("SELECT COUNT(r) FROM Reclamo r WHERE r.status = 'A' AND r.estado IN :estados")
    long countByEstados(@Param("estados") List<EstadoReclamo> estados);
}
