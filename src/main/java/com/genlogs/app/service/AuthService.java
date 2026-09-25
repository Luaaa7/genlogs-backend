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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final short MAX_INTENTOS_FALLIDOS = 5;

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(request.getNombreUsuario())
                .orElseThrow(() -> new BusinessException("Usuario o contraseña incorrectos"));

        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            throw new BusinessException("El usuario está bloqueado. Contacta al administrador.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getNombreUsuario(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            registrarIntentoFallido(usuario);
            throw new BusinessException("Usuario o contraseña incorrectos");
        }

        if (usuario.getIntentosFallidos() > 0) {
            usuario.setIntentosFallidos((short) 0);
            usuarioRepository.save(usuario);
        }

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(usuario.getNombreUsuario())
                .password(usuario.getPasswordHash())
                .authorities("ROLE_" + usuario.getRol().getNombreRol().toUpperCase())
                .build();

        String token = jwtUtil.generarToken(userDetails);

        return new LoginResponse(token, usuario.getNombreUsuario(), usuario.getRol().getNombreRol());
    }

    private void registrarIntentoFallido(Usuario usuario) {
        short intentos = (short) (usuario.getIntentosFallidos() + 1);
        usuario.setIntentosFallidos(intentos);
        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            usuario.setBloqueado(true);
        }
        usuarioRepository.save(usuario);
    }
}