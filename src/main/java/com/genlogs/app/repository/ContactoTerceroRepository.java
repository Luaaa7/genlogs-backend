package com.genlogs.app.repository;

import com.genlogs.app.model.ContactoTercero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactoTerceroRepository extends JpaRepository<ContactoTercero, Long> {

    List<ContactoTercero> findByTercero_IdTerceroAndStatus(Long idTercero, String status);

    Optional<ContactoTercero> findByTercero_IdTerceroAndEsPrincipalTrueAndStatus(
            Long idTercero, String status);
}