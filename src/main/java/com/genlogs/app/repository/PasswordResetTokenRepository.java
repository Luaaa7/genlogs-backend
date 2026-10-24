package com.genlogs.app.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.PasswordResetToken;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Para restablecer: bloquea la fila para que dos clics simultáneos no usen el mismo token dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.usuario where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Para validar el enlace al abrir la pantalla (solo lectura, sin bloqueo). */
    @Query("select t from PasswordResetToken t join fetch t.usuario where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashConUsuario(@Param("tokenHash") String tokenHash);

    void deleteByUsuario_IdUsuario(Long idUsuario);

    void deleteByExpiresAtBefore(LocalDateTime fecha);
}
