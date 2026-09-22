package com.genlogs.app.repository;

import com.genlogs.app.model.ServicioImagen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicioImagenRepository extends JpaRepository<ServicioImagen, Long> {

    List<ServicioImagen> findByServicio_IdServicio(Long idServicio);
}
