package com.genlogs.app.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.SolicitudCreadaResponse;
import com.genlogs.app.dto.SolicitudRequest;
import com.genlogs.app.service.SolicitudService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint único de solicitudes (BE-B).
 *
 * POST /api/solicitudes (cuerpo SolicitudRequest)
 *   201 -> SolicitudCreadaResponse
 *   400 -> validación o regla D-01 por canal (message y errores por campo)
 *   401 -> sin sesión; la landing manda al registro con ?volver= (D-30)
 *   403 -> no es un cliente del portal activo
 *   429 -> más de 10 solicitudes en 15 minutos
 *
 * POST /api/solicitudes/fotos (multipart "file": JPG, PNG, WEBP o PDF, hasta 10 MB)
 *   200 -> {"url": "..."} para mandar en items[].urlFoto
 *   400 -> archivo vacío, de otro tipo o muy pesado
 */
@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;

    @PostMapping
    public ResponseEntity<SolicitudCreadaResponse> crear(@Valid @RequestBody SolicitudRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crear(request));
    }

    @PostMapping(value = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> subirFoto(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("url", solicitudService.subirFoto(file)));
    }
}
