package com.genlogs.app.service;

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
import org.springframework.stereotype.Service;

import com.genlogs.app.dto.IngresoClienteRequest;
import com.genlogs.app.dto.IngresoClienteResponse;
import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.CredencialesInvalidasException;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Login. La recuperación de contraseña vive en {@link PasswordResetService}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ROL_CLIENTE = "CLIENTE";

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;

    /** A la quinta contraseña incorrecta seguida, el usuario queda bloqueado (RF-28). */
    @Value("${app.security.max-intentos-fallidos:5}")
    private int maxIntentosFallidos;

    public LoginResponse login(LoginRequest request) {
        // Se busca antes de autenticar para poder registrar el intento fallido
        // sobre el usuario correcto (RF-28: bloqueo automático tras varios
        // intentos seguidos con contraseña incorrecta).
        Usuario usuario = usuarioRepository.findByNombreUsuario(request.getNombreUsuario()).orElse(null);
        autenticar(usuario, request.getNombreUsuario(), request.getPassword());
        return new LoginResponse(emitirToken(usuario), usuario.getNombreUsuario(), usuario.getRol().getNombreRol());
    }

    /**
     * Ingreso del cliente al portal (POST /api/portal/login), separado del login
     * interno: entra con su correo y solo si su rol es CLIENTE. A un usuario
     * interno se le responde igual que a una contraseña incorrecta, para no
     * revelar qué correos son del personal.
     */
    public IngresoClienteResponse loginCliente(IngresoClienteRequest request) {
        Usuario usuario = usuarioRepository.findWithRolByCorreoIgnoreCase(request.getCorreo().trim()).orElse(null);
        if (usuario == null || !ROL_CLIENTE.equalsIgnoreCase(usuario.getRol().getNombreRol())) {
            throw new CredencialesInvalidasException();
        }
        autenticar(usuario, usuario.getNombreUsuario(), request.getPassword());
        return new IngresoClienteResponse(emitirToken(usuario), usuario.getNombreUsuario(),
                usuario.getNombres(), usuario.getRol().getNombreRol());
    }

    /** Token JWT del usuario (login interno, ingreso del cliente y registro con acceso inmediato). */
    public String emitirToken(Usuario usuario) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(usuario.getNombreUsuario())
                .password(usuario.getPasswordHash())
                .authorities("ROLE_" + usuario.getRol().getNombreRol().toUpperCase())
                .build();
        return jwtUtil.generarToken(userDetails);
    }

    /**
     * Valida usuario y contraseña con Spring Security: cuenta los intentos fallidos
     * (RF-28), rechaza inactivos y bloqueados y, si entra, reinicia el contador.
     */
    private void autenticar(Usuario usuario, String nombreUsuario, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(nombreUsuario, password)
            );
        } catch (BadCredentialsException ex) {
            if (usuario != null) {
                registrarIntentoFallido(usuario);
            }
            throw new CredencialesInvalidasException();
        } catch (DisabledException ex) {
            throw new BusinessException("El usuario está inactivo. Contacta al administrador.");
        } catch (LockedException ex) {
            throw new BusinessException("El usuario está bloqueado. Contacta al administrador.");
        } catch (AuthenticationException ex) {
            if (usuario != null) {
                registrarIntentoFallido(usuario);
            }
            throw new CredencialesInvalidasException();
        }

        if (usuario == null) {
            throw new CredencialesInvalidasException();
        }

        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            throw new BusinessException("El usuario está bloqueado. Contacta al administrador.");
        }

        if (usuario.getIntentosFallidos() != null && usuario.getIntentosFallidos() > 0) {
            usuario.setIntentosFallidos((short) 0);
            usuarioRepository.save(usuario);
        }
    }

    /**
     * Suma un intento fallido al usuario y, al llegar a maxIntentosFallidos
     * seguidos, lo bloquea automáticamente (RF-28). Un login exitoso
     * reinicia este contador a 0 (ver arriba); un administrador también
     * puede desbloquear manualmente desde Usuarios (UsuarioService.cambiarBloqueo).
     */
    private void registrarIntentoFallido(Usuario usuario) {
        int intentos = (usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos()) + 1;
        usuario.setIntentosFallidos((short) intentos);

        if (intentos >= maxIntentosFallidos) {
            usuario.setBloqueado(true);
            log.warn("Usuario '{}' bloqueado automáticamente tras {} intentos fallidos seguidos",
                    usuario.getNombreUsuario(), intentos);
        }

        usuarioRepository.save(usuario);
    }
}