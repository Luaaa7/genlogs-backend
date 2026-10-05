package com.genlogs.app.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    interface TotalesPorCotizacionProjection extends TotalesCotizacionProjection {
        Long getIdCotizacion();
    }

    @Query(value = "SELECT subtotal AS subtotal, igv AS igv, total AS total "
                 + "FROM vw_cotizacion_totales WHERE id_cotizacion = :id",
           nativeQuery = true)
    Optional<TotalesCotizacionProjection> findTotalesByCotizacion(@Param("id") Long id);

    @Query(value = "SELECT id_cotizacion AS idCotizacion, subtotal AS subtotal, igv AS igv, total AS total "
                 + "FROM vw_cotizacion_totales WHERE id_cotizacion IN (:ids)",
           nativeQuery = true)
    List<TotalesPorCotizacionProjection> findTotalesByCotizaciones(@Param("ids") Collection<Long> ids);
}
