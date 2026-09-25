package com.genlogs.app.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.PasswordResetToken;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.PasswordResetTokenRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final Duration DURACION_TOKEN = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String MENSAJE_TOKEN_INVALIDO =
            "El enlace venció o no es válido. Solicita uno nuevo.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordResetEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void solicitarReset(String correo) {
        Instant ahora = Instant.now();

        // Limpia tokens vencidos cada vez que alguien solicita una recuperación.
        resetTokenRepository.deleteByExpiresAtBefore(ahora);

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCaseAndStatus(correo.trim(), "A")
                .orElse(null);

        // Responder igual exista o no la cuenta evita revelar correos registrados.
        if (usuario == null) {
            return;
        }

        String tokenPlano = generarToken();

        // Si pide otro, el enlace anterior deja de servir.
        resetTokenRepository.deleteByUsuario_IdUsuario(usuario.getIdUsuario());

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUsuario(usuario);
        resetToken.setTokenHash(sha256(tokenPlano));
        resetToken.setCreatedAt(ahora);
        resetToken.setExpiresAt(ahora.plus(DURACION_TOKEN));
        resetTokenRepository.save(resetToken);

        try {
            emailService.enviarEnlaceReset(usuario.getCorreo(), tokenPlano);
        } catch (MailException ex) {
            // No devolver al cliente si el correo existe; revisar los logs del backend.
            log.error("No se pudo enviar el correo de recuperación", ex);
        }
    }

    @Transactional
    public void restablecer(String tokenPlano, String nuevaPassword) {
        String hash = sha256(tokenPlano);

        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new BusinessException(MENSAJE_TOKEN_INVALIDO));

        if (!resetToken.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(MENSAJE_TOKEN_INVALIDO);
        }

        Usuario usuario = resetToken.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueado(false);
        usuario.setUserUpdate("PASSWORD_RESET");
        usuario.setProcessUpdate("PASSWORD_RESET");
        usuario.setDateUpdate(LocalDateTime.now());

        usuarioRepository.save(usuario);

        // El token es de un solo uso.
        resetTokenRepository.delete(resetToken);
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No está disponible SHA-256", ex);
        }
    }
}