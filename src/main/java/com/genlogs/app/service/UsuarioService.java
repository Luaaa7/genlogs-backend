package com.genlogs.app.service;

import com.genlogs.app.dto.UsuarioRequest;
import com.genlogs.app.dto.UsuarioResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findByStatus("A").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return toResponse(obtenerOLanzar(id));
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        if (usuarioRepository.findByNombreUsuario(request.getNombreUsuario()).isPresent()) {
            throw new BusinessException("Ya existe un usuario con ese nombre de usuario");
        }
        if (usuarioRepository.findByCorreo(request.getCorreo()).isPresent()) {
            throw new BusinessException("Ya existe un usuario con ese correo");
        }

        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResourceNotFoundException("Rol no existe"));

        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombreUsuario(request.getNombreUsuario());
        usuario.setNombres(request.getNombres());
        usuario.setCorreo(request.getCorreo());
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setIniciales(request.getIniciales());
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setUserCreate(usuarioActual());
        usuario.setProcessCreate("ALTA_USUARIO");

        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        Usuario usuario = obtenerOLanzar(id);

        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResourceNotFoundException("Rol no existe"));

        usuario.setRol(rol);
        usuario.setNombres(request.getNombres());
        usuario.setCorreo(request.getCorreo());
        usuario.setIniciales(request.getIniciales());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("EDITAR_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());

        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarBloqueo(Long id, boolean bloqueado) {
        Usuario usuario = obtenerOLanzar(id);
        usuario.setBloqueado(bloqueado);
        if (!bloqueado) {
            usuario.setIntentosFallidos((short) 0);
        }
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate(bloqueado ? "BLOQUEAR_USUARIO" : "DESBLOQUEAR_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void desactivar(Long id) {
        Usuario usuario = obtenerOLanzar(id);
        usuario.setStatus("I");
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("BAJA_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    // ------------------------------------------------------------------

    private Usuario obtenerOLanzar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private UsuarioResponse toResponse(Usuario u) {
        return UsuarioResponse.builder()
                .idUsuario(u.getIdUsuario())
                .nombreUsuario(u.getNombreUsuario())
                .nombres(u.getNombres())
                .correo(u.getCorreo())
                .iniciales(u.getIniciales())
                .nombreRol(u.getRol().getNombreRol())
                .idRol(u.getRol().getIdRol())
                .bloqueado(u.getBloqueado())
                .intentosFallidos(u.getIntentosFallidos())
                .build();
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}