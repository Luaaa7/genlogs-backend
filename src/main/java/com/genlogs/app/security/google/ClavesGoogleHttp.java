package com.genlogs.app.security.google;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Descarga las claves públicas de Google (JWKS) y las guarda una hora. Si llega un kid
 * desconocido (Google rota sus claves), vuelve a descargar, como máximo una vez por minuto.
 */
@Component
public class ClavesGoogleHttp implements ClavesGoogle {

    private static final Logger log = LoggerFactory.getLogger(ClavesGoogleHttp.class);
    private static final URI CERTS = URI.create("https://www.googleapis.com/oauth2/v3/certs");
    private static final long VIGENCIA_MS = 60L * 60L * 1000L;
    private static final long ESPERA_RECARGA_MS = 60L * 1000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Map<String, Key> cache = Map.of();
    private volatile long cargadoEn;

    @Override
    public Key clave(String kid) {
        if (kid == null) {
            return null;
        }
        long ahora = System.currentTimeMillis();
        Key clave = cache.get(kid);
        boolean vencido = ahora - cargadoEn > VIGENCIA_MS;
        if (clave == null || vencido) {
            synchronized (this) {
                if ((cache.get(kid) == null && ahora - cargadoEn > ESPERA_RECARGA_MS) || ahora - cargadoEn > VIGENCIA_MS) {
                    recargar();
                }
            }
            clave = cache.get(kid);
        }
        return clave;
    }

    private void recargar() {
        try {
            HttpRequest pedido = HttpRequest.newBuilder(CERTS).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                log.warn("No se pudieron leer las claves de Google: HTTP {}", respuesta.statusCode());
                return;
            }
            cache = parsear(respuesta.body());
            cargadoEn = System.currentTimeMillis();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Lectura de las claves de Google interrumpida");
        } catch (Exception ex) {
            log.warn("No se pudieron leer las claves de Google ({})", ex.getClass().getSimpleName());
        }
    }

    /** Solo claves RSA; las demás se ignoran. */
    static Map<String, Key> parsear(String json) {
        Map<String, Key> claves = new HashMap<>();
        try {
            JsonNode raiz = JSON.readTree(json);
            KeyFactory fabrica = KeyFactory.getInstance("RSA");
            for (JsonNode jwk : raiz.path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asText()) || !jwk.hasNonNull("kid")) {
                    continue;
                }
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
                claves.put(jwk.path("kid").asText(), fabrica.generatePublic(new RSAPublicKeySpec(n, e)));
            }
        } catch (Exception ex) {
            log.warn("Claves de Google con formato inesperado ({})", ex.getClass().getSimpleName());
        }
        return Map.copyOf(claves);
    }
}
