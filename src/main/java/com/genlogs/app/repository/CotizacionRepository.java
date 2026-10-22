package com.genlogs.app.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.Cotizacion;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    Optional<Cotizacion> findByCodigoCotizacion(String codigoCotizacion);

    @Query("SELECT c FROM Cotizacion c WHERE c.cliente.idCliente = :idCliente AND c.status = 'A' ORDER BY c.fechaCotizacion DESC")
    List<Cotizacion> findByCliente(@Param("idCliente") Long idCliente);

    List<Cotizacion> findByDateCreateBetween(LocalDateTime inicio, LocalDateTime fin);

    // ---- Totales desde la vista (Requeridos por CotizacionService) ----

    interface TotalesCotizacionProjection {
        BigDecimal getSubtotal();
        BigDecimal getIgv();
        BigDecimal getTotal();
    }

    @Query(value = "SELECT descuento_total AS descuentoTotal, subtotal, igv, total FROM vw_cotizacion_totales WHERE id_cotizacion = :idCotizacion", nativeQuery = true)
    Optional<TotalesCotizacionProjection> findTotalesByCotizacion(@Param("idCotizacion") Long idCotizacion);

    // Projection for paginated list by cliente
    interface CotizacionListProjection {
        Long getIdCotizacion();
        String getCodigoCotizacion();
        java.time.LocalDate getFechaCotizacion();
        String getClienteRazonSocial();
        String getEstadoNombre();
        String getMonedaSimbolo();
        BigDecimal getTotal();
    }

    @Query(value = """
        SELECT c.id_cotizacion as idCotizacion,
               c.codigo_cotizacion as codigoCotizacion,
               c.fecha_cotizacion as fechaCotizacion,
               t.razon_social as clienteRazonSocial,
               ec.nombre_estado as estadoNombre,
               m.simbolo as monedaSimbolo,
               v.total as total
        FROM cotizacion c
        JOIN cliente cl ON cl.id_cliente = c.id_cliente
        JOIN tercero t ON t.id_tercero = cl.id_tercero
        JOIN estado_cotizacion ec ON ec.id_estado_cotizacion = c.id_estado_cotizacion
        JOIN moneda m ON m.id_moneda = c.id_moneda
        LEFT JOIN vw_cotizacion_totales v ON v.id_cotizacion = c.id_cotizacion
        WHERE c.status = 'A' AND cl.id_cliente = :idCliente
        ORDER BY c.fecha_cotizacion DESC
        """, nativeQuery = true)
    Page<CotizacionListProjection> findByClientePaginado(@Param("idCliente") Long idCliente, Pageable pageable);

    /**
     * Listado paginado con filtros opcionales (cualquiera puede venir null).
     * Usada por GET /api/cotizaciones (CotizacionesListPage.tsx del frontend).
     */
    @Query("SELECT c FROM Cotizacion c WHERE c.status = 'A' " +
            "AND (:codigoEstado IS NULL OR c.estadoCotizacion.codigoEstado = :codigoEstado) " +
            "AND (:idCliente IS NULL OR c.cliente.idCliente = :idCliente) " +
            "AND (:codigoMoneda IS NULL OR c.moneda.codigoMoneda = :codigoMoneda)")
    Page<Cotizacion> buscarPaginado(@Param("codigoEstado") String codigoEstado,
                                     @Param("idCliente") Long idCliente,
                                     @Param("codigoMoneda") String codigoMoneda,
                                     Pageable pageable);

    // --- Soporte para DashboardController ---

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.status = 'A' AND c.estadoCotizacion.esFinal = false")
    long countPendientes();

    public interface EstadoCantidadProjection {
        String getEstado();
        Long getCantidad();
    }

    @Query("SELECT c.estadoCotizacion.nombreEstado AS estado, COUNT(c) AS cantidad " +
            "FROM Cotizacion c WHERE c.status = 'A' " +
            "GROUP BY c.estadoCotizacion.nombreEstado")
    List<EstadoCantidadProjection> countAgrupadoPorEstado();

    // --- Soporte para GET /api/cotizaciones/estadisticas ---

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.status = 'A'")
    long countActivas();

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.status = 'A' AND c.estadoCotizacion.codigoEstado = 'APROBADA'")
    long countAprobadas();

    /**
     * Suma el total (vw_cotizacion_totales) de las cotizaciones activas que
     * todavía no llegaron a un estado final (pendientes de cerrar).
     */
    @Query(value = "SELECT COALESCE(SUM(v.total), 0) " +
            "FROM cotizacion c " +
            "JOIN estado_cotizacion e ON e.id_estado_cotizacion = c.id_estado_cotizacion " +
            "JOIN vw_cotizacion_totales v ON v.id_cotizacion = c.id_cotizacion " +
            "WHERE c.status = 'A' AND e.es_final = false", nativeQuery = true)
    BigDecimal sumTotalPendiente();
}
