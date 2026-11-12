package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.AccionCorrectiva;

public interface AccionCorrectivaRepository extends JpaRepository<AccionCorrectiva, Long> {

    List<AccionCorrectiva> findByAnioAndMesOrderByFechaAscIdAccionAsc(Short anio, Short mes);
}
