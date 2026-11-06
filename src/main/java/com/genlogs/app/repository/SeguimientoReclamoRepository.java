package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.SeguimientoReclamo;

public interface SeguimientoReclamoRepository extends JpaRepository<SeguimientoReclamo, Long> {

    List<SeguimientoReclamo> findByReclamo_IdReclamoAndStatusOrderByFechaEventoAscIdSeguimientoReclamoAsc(
            Long idReclamo, String status);
}
