package com.genlogs.app.service;

import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MSG_ENLACE_INVALIDO =
            "El enlace venció o no es válido. Solicita uno nuevo desde el login.";

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getNombreUsuario(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BusinessException("Usuario o contraseña incorrectos");
        } catch (DisabledException ex) {
            throw new BusinessException("El usuario está inactivo. Contacta al administrador.");
        } catch (LockedException ex) {
            throw new BusinessException("El usuario está bloqueado. Contacta al administrador.");
        } catch (AuthenticationException ex) {
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

    /**
     * Genera el enlace de recuperación y lo envía por correo.
     * No lanza error si el correo no existe (para no revelar qué correos están registrados).
     */
    public void solicitarRecuperacion(String correo) {
        String correoLimpio = correo == null ? "" : correo.trim();

        usuarioRepository.findByCorreo(correoLimpio).ifPresentOrElse(usuario -> {
            if (!"A".equals(usuario.getStatus()) || Boolean.TRUE.equals(usuario.getBloqueado())) {
                log.info("Recuperación ignorada: usuario inactivo o bloqueado");
                return;
            }

            String token = jwtUtil.generarTokenReset(usuario.getNombreUsuario(), usuario.getPasswordHash());
            String base = frontendUrl.replaceAll("/+$", "");
            String link = base + "/reset-password?token=" + token;

            try {
                mailService.enviarRecuperacion(usuario.getCorreo(), usuario.getNombres(), link);
            } catch (Exception e) {
                log.error("Falló el envío del correo de recuperación", e);
            }
        }, () -> log.info("Recuperación solicitada para un correo no registrado"));
    }

    @Transactional
    public void restablecerPassword(String token, String nuevaPassword) {
        Claims claims;
        try {
            claims = jwtUtil.leerClaimsReset(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(MSG_ENLACE_INVALIDO);
        }

        Usuario usuario = usuarioRepository.findByNombreUsuario(claims.getSubject())
                .orElseThrow(() -> new BusinessException(MSG_ENLACE_INVALIDO));

        // Si la contraseña ya cambió desde que se emitió el enlace, el enlace deja de servir.
        if (!jwtUtil.huellaCoincide(claims, usuario.getPasswordHash())) {
            throw new BusinessException(MSG_ENLACE_INVALIDO);
        }

        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setIntentosFallidos((short) 0);
        usuario.setUserUpdate(usuario.getNombreUsuario());
        usuario.setProcessUpdate("RESET_PASSWORD");
        usuario.setDateUpdate(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }
}