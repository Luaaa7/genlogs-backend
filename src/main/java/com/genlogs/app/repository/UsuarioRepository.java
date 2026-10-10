package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    Optional<Usuario> findByCorreoIgnoreCaseAndStatus(String correo, String status);

    boolean existsByNombreUsuario(String nombreUsuario);

    boolean existsByCorreo(String correo);

    List<Usuario> findByStatus(String status);

    // Nuevos métodos indicados en el paso 2[cite: 1]
    boolean existsByCorreoIgnoreCaseAndIdUsuarioNot(String correo, Long idUsuario);
    
    Optional<Usuario> findByIdUsuarioAndStatus(Long idUsuario, String status);
}