package com.genlogs.app.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.Facturacion;

public interface FacturacionRepository extends JpaRepository<Facturacion, Long> {
    Optional<Facturacion> findBySerieComprobanteAndNumeroComprobante(String serie, String numero);

    @Query("SELECT f FROM Facturacion f WHERE f.ordenCompra.cotizacion.cliente.idCliente = :idCliente AND f.status = 'A' ORDER BY f.fechaEmision DESC")
    List<Facturacion> findByCliente(@Param("idCliente") Long idCliente);

    // Listado paginado con filtros opcionales ('' y 0 significan "sin filtro").
    @Query(value = "SELECT f FROM Facturacion f WHERE f.status = 'A' "
            + "AND (:codigoEstado = '' OR f.estadoFacturacion.codigoEstado = :codigoEstado) "
            + "AND (:codigoTipo = '' OR f.tipoComprobante.codigoTipo = :codigoTipo) "
            + "AND (:idOrdenCompra = 0L OR f.ordenCompra.idOrdenCompra = :idOrdenCompra) "
            + "ORDER BY f.fechaEmision DESC, f.idFacturacion DESC",
            countQuery = "SELECT COUNT(f) FROM Facturacion f WHERE f.status = 'A' "
            + "AND (:codigoEstado = '' OR f.estadoFacturacion.codigoEstado = :codigoEstado) "
            + "AND (:codigoTipo = '' OR f.tipoComprobante.codigoTipo = :codigoTipo) "
            + "AND (:idOrdenCompra = 0L OR f.ordenCompra.idOrdenCompra = :idOrdenCompra)")
    Page<Facturacion> buscar(@Param("codigoEstado") String codigoEstado,
                             @Param("codigoTipo") String codigoTipo,
                             @Param("idOrdenCompra") long idOrdenCompra,
                             Pageable pageable);

    // --- Soporte para DashboardController ---
    // La tabla "facturacion" no guarda el total: se calcula en la vista
    // vw_facturacion_totales (igual que vw_cotizacion_totales para cotizacion).

    @Query(value = "SELECT COALESCE(SUM(total), 0) FROM vw_facturacion_totales " +
            "WHERE (CAST(:desde AS date) IS NULL OR fecha_emision >= :desde) " +
            "AND (CAST(:hasta AS date) IS NULL OR fecha_emision <= :hasta)", nativeQuery = true)
    BigDecimal sumTotalFacturado(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query(value = "SELECT COALESCE(SUM(total), 0) FROM vw_facturacion_totales " +
            "WHERE date_trunc('month', fecha_emision) = date_trunc('month', CAST(:fecha AS date))",
            nativeQuery = true)
    BigDecimal sumTotalFacturadoDelMes(@Param("fecha") LocalDate fecha);

    public interface PeriodoMontoProjection {
        String getPeriodo();
        BigDecimal getMonto();
    }

    @Query(value = "SELECT to_char(date_trunc('month', fecha_emision), 'YYYY-MM') AS periodo, " +
            "COALESCE(SUM(total), 0) AS monto " +
            "FROM vw_facturacion_totales WHERE fecha_emision >= :desde " +
            "GROUP BY date_trunc('month', fecha_emision) " +
            "ORDER BY date_trunc('month', fecha_emision)", nativeQuery = true)
    List<PeriodoMontoProjection> sumMensualDesde(@Param("desde") LocalDate desde);

    @Query(value = "SELECT to_char(date_trunc('quarter', fecha_emision), 'YYYY') || '-Q' || " +
            "to_char(date_trunc('quarter', fecha_emision), 'Q') AS periodo, " +
            "COALESCE(SUM(total), 0) AS monto " +
            "FROM vw_facturacion_totales WHERE fecha_emision >= :desde " +
            "GROUP BY date_trunc('quarter', fecha_emision) " +
            "ORDER BY date_trunc('quarter', fecha_emision)", nativeQuery = true)
    List<PeriodoMontoProjection> sumTrimestralDesde(@Param("desde") LocalDate desde);

    @Query(value = "SELECT to_char(date_trunc('year', fecha_emision), 'YYYY') AS periodo, " +
            "COALESCE(SUM(total), 0) AS monto " +
            "FROM vw_facturacion_totales WHERE fecha_emision >= :desde " +
            "GROUP BY date_trunc('year', fecha_emision) " +
            "ORDER BY date_trunc('year', fecha_emision)", nativeQuery = true)
    List<PeriodoMontoProjection> sumAnualDesde(@Param("desde") LocalDate desde);
}
