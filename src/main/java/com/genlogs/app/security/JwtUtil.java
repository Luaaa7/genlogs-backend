package com.genlogs.app.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

/**
 * Tokens JWT de sesión. La recuperación de contraseña YA NO usa JWT: ahora va
 * por la tabla password_reset_token (ver PasswordResetService).
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    /** Falla al arrancar (y no en el primer login) si JWT_SECRET es demasiado corto para HS256. */
    @PostConstruct
    void validarSecreto() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret debe tener al menos 32 caracteres (256 bits)");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extraerUsername(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    /**
     * El token sirve solo si corresponde al usuario, no venció y la cuenta sigue
     * habilitada y sin bloquear (así un usuario bloqueado o desactivado pierde el
     * acceso aunque su token no haya vencido).
     */
    public boolean esTokenValido(String token, UserDetails userDetails) {
        Claims claims = parsear(token);
        return claims.getSubject().equals(userDetails.getUsername())
                && !claims.getExpiration().before(new Date())
                && userDetails.isEnabled()
                && userDetails.isAccountNonLocked();
    }

    private Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private <T> T extraerClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parsear(token));
    }
}

