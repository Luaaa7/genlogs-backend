package com.genlogs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.genlogs.app.model.ParametroSistema;

@Repository
public interface ParametroSistemaRepository extends JpaRepository<ParametroSistema, String> {
}