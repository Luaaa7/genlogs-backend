package com.genlogs.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.CaracteristicaTecnica;

public interface CaracteristicaTecnicaRepository extends JpaRepository<CaracteristicaTecnica, Integer> {

    List<CaracteristicaTecnica> findByStatus(String status);
}
