package com.genlogs.app.repository;

import java.math.BigDecimal;
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

    /**
     * La tabla "cotizacion" no guarda el total: se calcula en la vista
     * vw_cotizacion_totales (subtotal = suma de importe_linea, igv = 18%,
     * total = subtotal * 1.18). Devuelve null si la cotización aún no
     * tiene líneas activas (LEFT JOIN + COALESCE en la vista igual
     * garantiza fila, pero se deja Optional por seguridad).
     */
    public interface TotalesCotizacionProjection {
        BigDecimal getDescuentoTotal();
        BigDecimal getSubtotal();
        BigDecimal getIgv();
        BigDecimal getTotal();
    }

    @Query(value = "SELECT descuento_total AS descuentoTotal, subtotal, igv, total " +
            "FROM vw_cotizacion_totales WHERE id_cotizacion = :idCotizacion", nativeQuery = true)
    Optional<TotalesCotizacionProjection> findTotalesByCotizacion(@Param("idCotizacion") Long idCotizacion);
}
