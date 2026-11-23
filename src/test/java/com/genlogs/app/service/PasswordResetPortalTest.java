package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.genlogs.app.dto.TokenResetInfoResponse;
import com.genlogs.app.model.PasswordResetToken;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.PasswordResetTokenRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** v3.4: al validar el enlace se sabe si la cuenta es del portal, para que «Ir al login» lleve al lugar correcto. */
class PasswordResetPortalTest {

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordResetService servicio = new PasswordResetService(
            mock(UsuarioRepository.class), tokens, mock(MailService.class), mock(PasswordEncoder.class));

    private TokenResetInfoResponse validarConRol(String nombreRol) {
        Rol rol = new Rol();
        rol.setNombreRol(nombreRol);
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setStatus("A");
        PasswordResetToken token = new PasswordResetToken();
        token.setUsuario(usuario);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(PasswordResetService.DURACION_MINUTOS));
        when(tokens.findByTokenHashConUsuario(anyString())).thenReturn(Optional.of(token));
        return servicio.validarToken("token-de-prueba");
    }

    @Test
    void unClienteVuelveAlPortal() {
        TokenResetInfoResponse info = validarConRol("CLIENTE");
        assertThat(info.isPortal()).isTrue();
        assertThat(info.getSegundosRestantes()).isPositive();
    }

    @Test
    void elPersonalVuelveAlLoginInterno() {
        assertThat(validarConRol("JEFE_ADMINISTRATIVO").isPortal()).isFalse();
    }

    @Test
    void sinRolNoEsDelPortal() {
        Usuario usuario = new Usuario();
        assertThat(PasswordResetService.esCliente(usuario)).isFalse();
    }
}
