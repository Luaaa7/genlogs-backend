package com.genlogs.app.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.SolicitudCotizacion;

public interface SolicitudCotizacionRepository extends JpaRepository<SolicitudCotizacion, Long> {

    @EntityGraph(attributePaths = "items")
    List<SolicitudCotizacion> findByCliente_IdClienteAndStatusOrderByDateCreateDesc(Long idCliente, String status);

    @EntityGraph(attributePaths = "items")
    Optional<SolicitudCotizacion> findByIdSolicitudAndCliente_IdClienteAndStatus(
            Long idSolicitud, Long idCliente, String status);

    boolean existsByCodigoSolicitud(String codigoSolicitud);

    /** Cotizaciones del mes que tienen solicitud ligada. La fecha límite puede ser null. */
    @Query("""
            SELECT c.idCotizacion AS idCotizacion,
                   c.fechaCotizacion AS fechaCotizacion,
                   s.fechaLimiteInterna AS fechaLimiteInterna
              FROM SolicitudCotizacion s
              JOIN s.cotizacion c
             WHERE s.status = 'A'
               AND c.status = 'A'
               AND c.fechaCotizacion >= :desde
               AND c.fechaCotizacion < :hasta
            """)
    List<PlazoFila> plazosDelMes(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    interface PlazoFila {
        Long getIdCotizacion();

        LocalDate getFechaCotizacion();

        LocalDateTime getFechaLimiteInterna();
    }
}
