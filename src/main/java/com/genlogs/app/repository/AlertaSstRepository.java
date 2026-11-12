package com.genlogs.app.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.model.DespachoSst;

/**
 * Consultas de solo lectura para las alertas de PASE y SCTR por vencer (BE-D).
 * Va aparte para no tocar DespachoSstRepository (BE-C) ni NotificacionRepository (Paso 0).
 */
public interface AlertaSstRepository extends Repository<DespachoSst, Long> {

    /** PASE y SCTR activos que vencen entre las dos fechas, de despachos que aún no se entregan. */
    @Query("""
            SELECT s FROM DespachoSst s
              JOIN FETCH s.despacho d
             WHERE s.status = 'A'
               AND d.status = 'A'
               AND s.fechaVencimiento BETWEEN :desde AND :hasta
               AND (d.etapa IS NULL OR d.etapa <> :entregado)
             ORDER BY s.fechaVencimiento, s.idDespachoSst
            """)
    List<DespachoSst> porVencer(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
            @Param("entregado") EtapaDespacho entregado);

    /** Avisos ya creados con ese tipo y título (el título identifica documento, fecha y umbral). */
    @Query("SELECT COUNT(n) FROM Notificacion n WHERE n.tipo = :tipo AND n.titulo = :titulo")
    long contarAvisos(@Param("tipo") String tipo, @Param("titulo") String titulo);
}
