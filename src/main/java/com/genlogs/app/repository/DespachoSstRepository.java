package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.enums.TipoSstDespacho;
import com.genlogs.app.model.DespachoSst;

public interface DespachoSstRepository extends JpaRepository<DespachoSst, Long> {

    List<DespachoSst> findByDespacho_IdDespachoAndStatus(Long idDespacho, String status);

    Optional<DespachoSst> findByDespacho_IdDespachoAndTipoAndStatus(
            Long idDespacho, TipoSstDespacho tipo, String status);
}
