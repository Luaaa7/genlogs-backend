package com.genlogs.app.security.google;

import java.security.Key;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.genlogs.app.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;

/**
 * Verifica el ID token de Google con sus claves públicas (RS256): firma, aplicación
 * (aud = GOOGLE_CLIENT_ID), emisor, vigencia y correo verificado. No usa el secreto
 * del cliente de Google: el ID token se valida solo con las claves públicas.
 * Si GOOGLE_CLIENT_ID está vacío, el ingreso con Google queda apagado.
 */
@Component
public class VerificadorTokenGoogleJwks implements VerificadorTokenGoogle {

    private static final Logger log = LoggerFactory.getLogger(VerificadorTokenGoogleJwks.class);
    private static final Set<String> EMISORES = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String NO_VALIDO = "No pudimos validar tu cuenta de Google. Inténtalo de nuevo.";

    private final ClavesGoogle claves;
    private final String clientId;

    @Autowired
    public VerificadorTokenGoogleJwks(ClavesGoogle claves, @Value("${app.google.client-id:}") String clientId) {
        this.claves = claves;
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    @Override
    public CuentaGoogle verificar(String idToken) {
        if (clientId.isEmpty()) {
            throw new BusinessException("El ingreso con Google no está disponible en este momento. Usa tu correo y contraseña.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(NO_VALIDO);
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            return claves.clave(header.getKeyId());
                        }
                    })
                    .requireAudience(clientId)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(idToken.trim())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // No se registra el token: es una credencial.
            log.info("Ingreso con Google rechazado: token no válido ({})", ex.getClass().getSimpleName());
            throw new BusinessException(NO_VALIDO);
        }
        if (!EMISORES.contains(claims.getIssuer())) {
            log.info("Ingreso con Google rechazado: emisor no reconocido");
            throw new BusinessException(NO_VALIDO);
        }
        String sub = claims.getSubject();
        String correo = claims.get("email", String.class);
        Object verificado = claims.get("email_verified");
        boolean correoVerificado = Boolean.TRUE.equals(verificado) || "true".equalsIgnoreCase(String.valueOf(verificado));
        if (sub == null || sub.isBlank()) {
            throw new BusinessException(NO_VALIDO);
        }
        if (!correoVerificado || correo == null || correo.isBlank()) {
            throw new BusinessException("Tu cuenta de Google no tiene un correo verificado. Usa otra cuenta o regístrate con tu correo.");
        }
        String nombre = claims.get("name", String.class);
        return new CuentaGoogle(sub, correo.trim().toLowerCase(java.util.Locale.ROOT),
                nombre == null || nombre.isBlank() ? null : nombre.trim());
    }
}
