package com.genlogs.app.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de estado y health check para Render y monitorización.
 * Permite que Render valide que el contenedor está listo y activo (HTTP 200)
 * sin requerir autenticación JWT.
 */
@RestController
public class HealthController {

    @GetMapping(value = {"/", "/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "genlogs-backend",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
