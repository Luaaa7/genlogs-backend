package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.OrdenCompra;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {
    Optional<OrdenCompra> findByNumeroOrdenCompra(String numeroOrdenCompra);

    boolean existsByCotizacion_IdCotizacionAndStatus(Long idCotizacion, String status);

    @Query("SELECT oc FROM OrdenCompra oc WHERE oc.cotizacion.cliente.idCliente = :idCliente AND oc.status = 'A' ORDER BY oc.fechaRecepcion DESC")
    List<OrdenCompra> findByCliente(@Param("idCliente") Long idCliente);

    // Listado paginado con filtros opcionales ('' y 0 significan "sin filtro").
    @Query(value = "SELECT oc FROM OrdenCompra oc WHERE oc.status = 'A' "
            + "AND (:codigoEstado = '' OR oc.estadoOrdenCompra.codigoEstado = :codigoEstado) "
            + "AND (:idCotizacion = 0L OR oc.cotizacion.idCotizacion = :idCotizacion) "
            + "ORDER BY oc.fechaRecepcion DESC, oc.idOrdenCompra DESC",
            countQuery = "SELECT COUNT(oc) FROM OrdenCompra oc WHERE oc.status = 'A' "
            + "AND (:codigoEstado = '' OR oc.estadoOrdenCompra.codigoEstado = :codigoEstado) "
            + "AND (:idCotizacion = 0L OR oc.cotizacion.idCotizacion = :idCotizacion)")
    Page<OrdenCompra> buscar(@Param("codigoEstado") String codigoEstado,
                             @Param("idCotizacion") long idCotizacion,
                             Pageable pageable);

    // --- Soporte para DashboardController ---

    @Query("SELECT COUNT(oc) FROM OrdenCompra oc WHERE oc.status = 'A' AND oc.estadoOrdenCompra.esFinal = false")
    long countEnProceso();
}
