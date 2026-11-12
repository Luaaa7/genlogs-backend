package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.SolicitudCotizacion;

public interface SolicitudCotizacionRepository extends JpaRepository<SolicitudCotizacion, Long> {

    @EntityGraph(attributePaths = "items")
    List<SolicitudCotizacion> findByCliente_IdClienteAndStatusOrderByDateCreateDesc(Long idCliente, String status);

    @EntityGraph(attributePaths = "items")
    Optional<SolicitudCotizacion> findByIdSolicitudAndCliente_IdClienteAndStatus(
            Long idSolicitud, Long idCliente, String status);

    boolean existsByCodigoSolicitud(String codigoSolicitud);

    /** Abiertas (PENDIENTE, EN_REVISION, EN_ESPERA_TERCERO) de la más urgente a la más holgada (BE-B). */
    List<SolicitudCotizacion> findByEstadoSolicitudInAndStatusOrderByFechaLimiteInternaAsc(
            java.util.Collection<String> estados, String status);
}
