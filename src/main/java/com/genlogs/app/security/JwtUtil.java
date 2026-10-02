package com.genlogs.app.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_HUELLA = "ph";
    private static final String PURPOSE_RESET = "reset";
    /** Minutos de vida del enlace de recuperación (también se muestra en el correo y en la pantalla). */
    public static final int RESET_EXPIRATION_MINUTES = 15;
    private static final long RESET_EXPIRATION_MS = RESET_EXPIRATION_MINUTES * 60L * 1000;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
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

    public boolean esTokenValido(String token, UserDetails userDetails) {
        Claims claims = parsear(token);
        // Un token de recuperación de contraseña NUNCA sirve para iniciar sesión.
        if (PURPOSE_RESET.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            return false;
        }
        return claims.getSubject().equals(userDetails.getUsername())
                && !claims.getExpiration().before(new Date());
    }

    // ----- Recuperación de contraseña (sin tablas nuevas en la BD) -----

    /**
     * Token de un solo uso lógico: lleva una "huella" del hash de contraseña actual.
     * Apenas la contraseña cambia, la huella ya no coincide y el enlace deja de servir.
     */
    public String generarTokenReset(String username, String passwordHashActual) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_PURPOSE, PURPOSE_RESET)
                .claim(CLAIM_HUELLA, huella(passwordHashActual))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + RESET_EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    /** Lanza JwtException si el token es inválido, venció o no es de recuperación. */
    public Claims leerClaimsReset(String token) {
        Claims claims = parsear(token);
        if (!PURPOSE_RESET.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            throw new JwtException("El token no es de recuperación de contraseña");
        }
        return claims;
    }

    public boolean huellaCoincide(Claims claims, String passwordHashActual) {
        String enToken = claims.get(CLAIM_HUELLA, String.class);
        return enToken != null && enToken.equals(huella(passwordHashActual));
    }

    private String huella(String passwordHash) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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