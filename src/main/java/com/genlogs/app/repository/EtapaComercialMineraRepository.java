package com.genlogs.app.repository;

import com.genlogs.app.model.EtapaComercialMinera;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EtapaComercialMineraRepository extends JpaRepository<EtapaComercialMinera, Integer> {

    List<EtapaComercialMinera> findByStatus(String status);

    boolean existsByNombreEtapaIgnoreCase(String nombreEtapa);
}
