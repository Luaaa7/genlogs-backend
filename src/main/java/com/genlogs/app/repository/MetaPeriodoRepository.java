package com.genlogs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.MetaPeriodo;
import com.genlogs.app.model.MetaPeriodoId;

public interface MetaPeriodoRepository extends JpaRepository<MetaPeriodo, MetaPeriodoId> {
}
