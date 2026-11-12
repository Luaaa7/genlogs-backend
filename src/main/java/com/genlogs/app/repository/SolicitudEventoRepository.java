package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.SolicitudEvento;

public interface SolicitudEventoRepository extends JpaRepository<SolicitudEvento, Long> {

    List<SolicitudEvento> findByIdSolicitudOrderByIdEventoAsc(Long idSolicitud);

    /** Último cambio de plazo (RECLASIFICA o REANUDA): desde ahí las alertas vuelven a contar. */
    java.util.Optional<SolicitudEvento> findFirstByIdSolicitudAndTipoEventoInOrderByFechaEventoDesc(
            Long idSolicitud, java.util.Collection<String> tipos);
}
