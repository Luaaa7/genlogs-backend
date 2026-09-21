package com.genlogs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Rol;

public interface RolRepository extends JpaRepository<Rol, Integer> {
}