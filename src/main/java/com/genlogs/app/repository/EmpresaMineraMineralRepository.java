package com.genlogs.app.repository;

import com.genlogs.app.model.EmpresaMineraMineral;
import com.genlogs.app.model.EmpresaMineraMineralId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmpresaMineraMineralRepository extends JpaRepository<EmpresaMineraMineral, EmpresaMineraMineralId> {

    List<EmpresaMineraMineral> findByEmpresaMinera_IdEmpresaMinera(Long idEmpresaMinera);
}

