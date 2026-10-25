package com.genlogs.app.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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

    boolean existsByUsuarioVendedor_IdUsuario(Long idUsuario);

    @Query("SELECT c FROM Cotizacion c WHERE c.cliente.idCliente = :idCliente AND c.status = 'A' ORDER BY c.fechaCotizacion DESC")
    List<Cotizacion> findByCliente(@Param("idCliente") Long idCliente);

    List<Cotizacion> findByDateCreateBetween(LocalDateTime inicio, LocalDateTime fin);

    /**
     * Cotizaciones activas del periodo, ordenadas por fecha de cotización.
     * El filtro de fechas se hace en la BD (no cargando toda la tabla y
     * filtrando en Java): lo usa el módulo de Reportes.
     */
    List<Cotizacion> findByStatusAndFechaCotizacionBetweenOrderByFechaCotizacionAsc(
            String status, LocalDate desde, LocalDate hasta);

    // ---- Totales desde la vista (Requeridos por CotizacionService) ----

    interface TotalesCotizacionProjection {
        BigDecimal getSubtotal();
        BigDecimal getIgv();
        BigDecimal getTotal();
    }

    /**
     * Totales de UNA cotización. Solo se piden las columnas que la proyección
     * expone: si la vista tuviera columnas extra (p.ej. descuento_total) el
     * SELECT las traería y PostgreSQL fallaría con "column does not exist"
     * si la vista no la expone, devolviendo 500 en todo el módulo.
     */
    @Query(value = "SELECT subtotal, igv, total FROM vw_cotizacion_totales WHERE id_cotizacion = :idCotizacion", nativeQuery = true)
    Optional<TotalesCotizacionProjection> findTotalesByCotizacion(@Param("idCotizacion") Long idCotizacion);

    /**
     * Totales de TODAS las cotizaciones de una página en una sola consulta
     * (evita el N+1 de toResponse: una query a la vista por cada fila).
     * La colección nunca debe ir vacía: un "IN ()" es SQL inválido.
     */
    @Query(value = "SELECT id_cotizacion AS \"idCotizacion\", subtotal, igv, total " +
            "FROM vw_cotizacion_totales WHERE id_cotizacion IN (:idsCotizaciones)", nativeQuery = true)
    List<TotalesCotizacionPorIdProjection> findTotalesPorCotizaciones(
            @Param("idsCotizaciones") Collection<Long> idsCotizaciones);

    /** Igual que {@link TotalesCotizacionProjection} pero con la clave de la cotización. */
    interface TotalesCotizacionPorIdProjection extends TotalesCotizacionProjection {
        Long getIdCotizacion();
    }

    /**
     * Código tal como quedó en la BD: lo asigna un trigger de PostgreSQL y
     * la entidad en memoria lo sigue teniendo en null.
     */
    @Query(value = "SELECT codigo_cotizacion FROM cotizacion WHERE id_cotizacion = :idCotizacion", nativeQuery = true)
    Optional<String> findCodigoCotizacionGenerado(@Param("idCotizacion") Long idCotizacion);

    /**
     * Listado paginado con filtros opcionales (cualquiera puede venir null).
     * Usada por GET /api/cotizaciones (CotizacionesListPage.tsx del frontend).
     *
     * El cliente se trae con FETCH JOIN (cliente + tercero) para no disparar
     * una consulta por fila al armar la respuesta. Al ser asociaciones a uno
     * (no colecciones) no multiplican filas, por eso la paginación sigue
     * siendo correcta; el countQuery va explícito para no arrastrar los
     * fetch joins.
     */
    @Query(value = "SELECT c FROM Cotizacion c " +
            "LEFT JOIN FETCH c.cliente cl " +
            "LEFT JOIN FETCH cl.tercero " +
            "LEFT JOIN FETCH c.estadoCotizacion " +
            "LEFT JOIN FETCH c.moneda " +
            "LEFT JOIN FETCH c.condicionPago " +
            "LEFT JOIN FETCH c.contacto " +
            "WHERE c.status = 'A' " +
            "AND (:codigoEstado IS NULL OR c.estadoCotizacion.codigoEstado = :codigoEstado) " +
            "AND (:idCliente IS NULL OR c.cliente.idCliente = :idCliente) " +
            "AND (:codigoMoneda IS NULL OR c.moneda.codigoMoneda = :codigoMoneda)",
            countQuery = "SELECT COUNT(c) FROM Cotizacion c WHERE c.status = 'A' " +
                    "AND (:codigoEstado IS NULL OR c.estadoCotizacion.codigoEstado = :codigoEstado) " +
                    "AND (:idCliente IS NULL OR c.cliente.idCliente = :idCliente) " +
                    "AND (:codigoMoneda IS NULL OR c.moneda.codigoMoneda = :codigoMoneda)")
    Page<Cotizacion> buscarPaginado(@Param("codigoEstado") String codigoEstado,
                                     @Param("idCliente") Long idCliente,
                                     @Param("codigoMoneda") String codigoMoneda,
                                     Pageable pageable);

    // --- Soporte para DashboardController ---

    @Query("SELECT COUNT(c) FROM Cotizacion c WHERE c.status = 'A' AND c.estadoCotizacion.esFinal = false AND (c.fechaValidez IS NULL OR c.fechaValidez >= CURRENT_DATE)")
    long countPendientes();

    List<Cotizacion> findByStatusAndEstadoCotizacionEsFinalFalseAndFechaValidezBefore(String status, LocalDate fecha);

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
     *
     * El JOIN con la vista es LEFT: una cotización pendiente sin líneas aún
     * no tiene fila en vw_cotizacion_totales y con INNER JOIN quedaría fuera
     * del conteo (el monto pendiente salía subvaluado).
     */
    @Query(value = "SELECT COALESCE(SUM(v.total), 0) " +
            "FROM cotizacion c " +
            "JOIN estado_cotizacion e ON e.id_estado_cotizacion = c.id_estado_cotizacion " +
            "LEFT JOIN vw_cotizacion_totales v ON v.id_cotizacion = c.id_cotizacion " +
            "WHERE c.status = 'A' AND e.es_final = false AND (c.fecha_validez IS NULL OR c.fecha_validez >= CURRENT_DATE)", nativeQuery = true)
    BigDecimal sumTotalPendiente();
}