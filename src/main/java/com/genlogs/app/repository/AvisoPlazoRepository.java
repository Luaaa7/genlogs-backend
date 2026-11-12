package com.genlogs.app.repository;

import java.time.LocalDateTime;

import org.springframework.data.repository.Repository;

import com.genlogs.app.model.Notificacion;

/**
 * Solo lectura sobre notificacion para no repetir alertas de plazo (BE-B). Va aparte para no
 * tocar NotificacionRepository, que es de BE-D.
 */
public interface AvisoPlazoRepository extends Repository<Notificacion, Long> {

    boolean existsByTipoAndEnlaceAndFechaCreacionAfter(String tipo, String enlace, LocalDateTime desde);
}
