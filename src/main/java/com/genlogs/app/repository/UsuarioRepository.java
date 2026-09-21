package com.genlogs.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    boolean existsByNombreUsuario(String nombreUsuario);

    boolean existsByCorreo(String correo);
}