package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.ContactoTercero;

public interface ContactoTerceroRepository extends JpaRepository<ContactoTercero, Long> {

    List<ContactoTercero> findByTercero_IdTerceroAndStatus(Long idTercero, String status);

    Optional<ContactoTercero> findByTercero_IdTerceroAndEsPrincipalTrueAndStatus(
            Long idTercero, String status);

    List<ContactoTercero> findByTercero_IdTercero(Long idTercero);
}