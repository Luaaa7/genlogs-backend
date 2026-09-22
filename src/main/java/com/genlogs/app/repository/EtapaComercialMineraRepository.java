package com.genlogs.app.repository;

import com.genlogs.app.model.EtapaComercialMinera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EtapaComercialMineraRepository extends JpaRepository<EtapaComercialMinera, Integer> {

    List<EtapaComercialMinera> findByStatus(String status);

    boolean existsByNombreEtapaIgnoreCase(String nombreEtapa);
}
