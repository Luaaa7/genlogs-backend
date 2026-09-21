package com.genlogs.app.service;

import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getNombreUsuario(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BusinessException("Usuario o contraseña incorrectos");
        }

        Usuario usuario = usuarioRepository.findByNombreUsuario(request.getNombreUsuario())
                .orElseThrow(() -> new BusinessException("Usuario o contraseña incorrectos"));

        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            throw new BusinessException("El usuario está bloqueado. Contacta al administrador.");
        }

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(usuario.getNombreUsuario())
                .password(usuario.getPasswordHash())
                .authorities("ROLE_" + usuario.getRol().getNombreRol().toUpperCase())
                .build();

        String token = jwtUtil.generarToken(userDetails);

        return new LoginResponse(token, usuario.getNombreUsuario(), usuario.getRol().getNombreRol());
    }
}