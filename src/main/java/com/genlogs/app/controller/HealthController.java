package com.genlogs.app.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints de estado, públicos (sin JWT):
 * <ul>
 *   <li>{@code /}, {@code /health}, {@code /api/health}: el proceso está vivo. Lo usa Render
 *       y el ping "keep-alive" (GitHub Action / UptimeRobot) para que el servicio no se duerma.</li>
 *   <li>{@code /api/health/db}: además ejecuta {@code SELECT 1} en Supabase. Devuelve 503 si la
 *       base no responde, así el monitor avisa; y la consulta periódica evita que Supabase
 *       pause el proyecto por inactividad.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    @GetMapping(value = {"/", "/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "genlogs-backend",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/api/health/db")
    public ResponseEntity<Map<String, Object>> healthBaseDeDatos() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "genlogs-backend");
        body.put("timestamp", LocalDateTime.now().toString());
        long inicio = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("status", "UP");
            body.put("database", "UP");
            body.put("latenciaMs", System.currentTimeMillis() - inicio);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Health check: la base de datos no responde", e);
            body.put("status", "DOWN");
            body.put("database", "DOWN");
            body.put("message", "La base de datos no responde.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
