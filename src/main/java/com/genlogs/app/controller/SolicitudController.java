package com.genlogs.app.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.SolicitudRequest;

import jakarta.validation.Valid;

/**
 * Endpoint unico de solicitudes (Paso 0: solo la firma). Lo implementa BE-B en
 * feat/be-b-endpoint-solicitudes; hasta entonces responde 501.
 *
 * Contrato de POST /api/solicitudes:
 *   Cuerpo: SolicitudRequest
 *   201 -> SolicitudCreadaResponse
 *   400 -> validacion (message y errores por campo)
 *   401 -> sin sesion; la landing manda al registro con ?volver= (D-30)
 *   403 -> rol sin permiso
 *   429 -> demasiadas solicitudes seguidas
 * Pueden crear: CLIENTE y el personal interno (regla en SecurityConfig).
 */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    @PostMapping
    public ResponseEntity<Object> crear(@Valid @RequestBody SolicitudRequest request) {
        // BE-B: reemplazar por ResponseEntity.status(HttpStatus.CREATED).body(new SolicitudCreadaResponse(...)).
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", HttpStatus.NOT_IMPLEMENTED.value());
        cuerpo.put("message", "El registro de solicitudes por esta ruta todavía no está disponible.");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(cuerpo);
    }
}