package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.UsuarioRequest;
import com.genlogs.app.dto.UsuarioResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByStatus("A").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        if (usuarioRepository.existsByNombreUsuario(request.getNombreUsuario())) {
            throw new BusinessException("Ya existe un usuario con ese nombre de usuario");
        }
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BusinessException("Ya existe un usuario con ese correo");
        }

        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResourceNotFoundException("El rol indicado no existe"));

        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombreUsuario(request.getNombreUsuario());
        usuario.setNombres(request.getNombres());
        usuario.setCorreo(request.getCorreo());
        usuario.setIniciales(request.getIniciales());
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setUserCreate(usuarioActual());
        usuario.setProcessCreate("ALTA_USUARIO");

        usuario = usuarioRepository.save(usuario);
        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse cambiarBloqueo(Long idUsuario, boolean bloqueado) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        usuario.setBloqueado(bloqueado);
        if (!bloqueado) {
            usuario.setIntentosFallidos((short) 0); // desbloquear resetea el contador
        }
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("CAMBIO_BLOQUEO_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());

        usuario = usuarioRepository.save(usuario);
        return toResponse(usuario);
    }

    // ------------------------------------------------------------------

    private UsuarioResponse toResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .idUsuario(usuario.getIdUsuario())
                .nombreUsuario(usuario.getNombreUsuario())
                .nombres(usuario.getNombres())
                .correo(usuario.getCorreo())
                .nombreRol(usuario.getRol().getNombreRol())
                .bloqueado(usuario.getBloqueado())
                .build();
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}