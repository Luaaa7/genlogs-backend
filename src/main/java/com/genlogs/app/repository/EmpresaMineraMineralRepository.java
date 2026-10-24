package com.genlogs.app.repository;

import com.genlogs.app.model.EmpresaMineraMineral;
import com.genlogs.app.model.EmpresaMineraMineralId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmpresaMineraMineralRepository extends JpaRepository<EmpresaMineraMineral, EmpresaMineraMineralId> {

    List<EmpresaMineraMineral> findByEmpresaMinera_IdEmpresaMinera(Long idEmpresaMinera);
}

