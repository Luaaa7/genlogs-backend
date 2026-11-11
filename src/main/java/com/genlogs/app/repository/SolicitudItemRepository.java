package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.SolicitudItem;

public interface SolicitudItemRepository extends JpaRepository<SolicitudItem, Long> {

    List<SolicitudItem> findBySolicitud_IdSolicitudAndStatus(Long idSolicitud, String status);
}
