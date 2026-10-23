package com.genlogs.app.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.genlogs.app.dto.TokenResetInfoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.PasswordResetToken;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.PasswordResetTokenRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recuperación de contraseña con tabla password_reset_token (un solo uso real,
 * un token vigente por usuario, solo se guarda el SHA-256 del token).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    /** Minutos de vida del enlace (también se muestra en el correo y en la pantalla). */
    public static final int DURACION_MINUTOS = 15;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String MSG_ENLACE_INVALIDO =
            "El enlace venció o no es válido. Solicita uno nuevo desde el login.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Genera el enlace y lo envía por correo. No lanza error si el correo no existe
     * (para no revelar qué correos están registrados).
     */
    @Transactional
    public void solicitarReset(String correo) {
        // Limpieza de tokens vencidos cada vez que alguien solicita una recuperación.
        resetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        String correoLimpio = correo == null ? "" : correo.trim();
       
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCaseAndStatus(correoLimpio, "A").orElse(null);

        if (usuario == null) {

            log.info("Recuperación solicitada para un correo no registrado o inactivo");
            return;
        }
        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            log.info("Recuperación ignorada: usuario bloqueado");
            return;
        }

        String tokenPlano = generarToken();

        // Si pide otro, el enlace anterior deja de servir. El flush es necesario: sin él
        // Hibernate ejecuta el INSERT antes que el DELETE y choca con UNIQUE(id_usuario).
        resetTokenRepository.deleteByUsuario_IdUsuario(usuario.getIdUsuario());
        resetTokenRepository.flush();

        LocalDateTime ahora = LocalDateTime.now();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUsuario(usuario);
        resetToken.setTokenHash(sha256(tokenPlano));
        resetToken.setCreatedAt(ahora);
        resetToken.setExpiresAt(ahora.plusMinutes(DURACION_MINUTOS));
        // La solicitud la hace un visitante sin sesión (endpoint público),
        // por eso no hay un usuario autenticado del que tomar el nombre.
        resetToken.setStatus("A");
        resetToken.setUserCreate("PUBLICO");
        resetToken.setProcessCreate("SOLICITAR_RESET_PASSWORD");
        resetToken.setDateCreate(ahora);
        resetTokenRepository.save(resetToken);

        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + tokenPlano;
        String destino = usuario.getCorreo();
        String nombres = usuario.getNombres();

        // El correo sale DESPUÉS del commit: así no mantenemos una conexión del pool
        // (son pocas en Supabase) abierta durante la llamada HTTP a Brevo, y nunca se
        // envía un enlace cuyo token no llegó a guardarse.
        enviarDespuesDelCommit(() -> {
            try {
                mailService.enviarRecuperacion(destino, nombres, link);
            } catch (Exception e) {
                log.error("Falló el envío del correo de recuperación", e);
            }
        });
    }

    /** La pantalla de "nueva contraseña" lo consulta al abrirse: indica si el enlace sigue vigente. */
    @Transactional(readOnly = true)
    public TokenResetInfoResponse validarToken(String tokenPlano) {
        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashConUsuario(sha256(tokenPlano.trim()))
                .orElseThrow(() -> enlaceInvalido("token no encontrado (inexistente o ya usado)"));

        verificarVigente(resetToken);
        verificarUsuarioHabilitado(resetToken.getUsuario());

        long segundos = Math.max(0, Duration.between(LocalDateTime.now(), resetToken.getExpiresAt()).getSeconds());
        return new TokenResetInfoResponse(segundos);
    }

    /** La pantalla de "nueva contraseña" lo consulta al abrirse: indica si el enlace sigue vigente. */
    @Transactional(readOnly = true)
    public TokenResetInfoResponse validarToken(String tokenPlano) {
        String hash = sha256(tokenPlano);

        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new BusinessException(MENSAJE_TOKEN_INVALIDO));

        long segundosRestantes = Duration.between(Instant.now(), resetToken.getExpiresAt()).getSeconds();
        if (segundosRestantes <= 0) {
            throw new BusinessException(MENSAJE_TOKEN_INVALIDO);
        }

        return new TokenResetInfoResponse(segundosRestantes);
    }

    @Transactional
    public void restablecer(String tokenPlano, String nuevaPassword) {
        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashForUpdate(sha256(tokenPlano.trim()))
                .orElseThrow(() -> enlaceInvalido("token no encontrado (inexistente o ya usado)"));

        verificarVigente(resetToken);

        Usuario usuario = resetToken.getUsuario();
        verificarUsuarioHabilitado(usuario);

        if (passwordEncoder.matches(nuevaPassword, usuario.getPasswordHash())) {
            throw new BusinessException("La nueva contraseña debe ser distinta a la anterior.");
        }

        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setIntentosFallidos((short) 0);
        // Un reset NO desbloquea al usuario: si un administrador lo bloqueó, sigue bloqueado.
        usuario.setUserUpdate(usuario.getNombreUsuario());
        usuario.setProcessUpdate("RESET_PASSWORD");
        usuario.setDateUpdate(LocalDateTime.now());
        usuarioRepository.save(usuario);

        // Un solo uso.
        resetTokenRepository.delete(resetToken);
    }

   

    private void verificarVigente(PasswordResetToken resetToken) {
        if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw enlaceInvalido("token vencido");
        }
    }

    private void verificarUsuarioHabilitado(Usuario usuario) {
        if (!"A".equals(usuario.getStatus()) || Boolean.TRUE.equals(usuario.getBloqueado())) {
            throw enlaceInvalido("usuario inactivo o bloqueado");
        }
    }

    private BusinessException enlaceInvalido(String motivo) {
        log.warn("Enlace de recuperación rechazado: {}", motivo);
        return new BusinessException(MSG_ENLACE_INVALIDO);
    }

    private void enviarDespuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No está disponible SHA-256", ex);
        }
    }
}
