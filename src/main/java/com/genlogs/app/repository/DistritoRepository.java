package com.genlogs.app.repository;

import com.genlogs.app.model.Distrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DistritoRepository extends JpaRepository<Distrito, Integer> {
    List<Distrito> findByProvincia_IdProvincia(Integer idProvincia);
    Optional<Distrito> findByUbigeo(String ubigeo);

    /** Todo el catálogo con su provincia y departamento (para buscar un distrito por sus nombres). */
    @Query("SELECT d FROM Distrito d JOIN FETCH d.provincia p JOIN FETCH p.departamento")
    List<Distrito> listarConProvinciaYDepartamento();
}
