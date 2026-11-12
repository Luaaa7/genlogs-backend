package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CalificacionReclamoRequest;
import com.genlogs.app.dto.ReclamoPortalRequest;
import com.genlogs.app.dto.ReclamoPortalResponse;
import com.genlogs.app.service.ReclamoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * RF-70: reclamos y calificación desde el portal del cliente. El cliente solo
 * ve y toca los reclamos de su empresa (lo filtra ReclamoService con su id_cliente).
 */
@RestController
@RequestMapping("/api/portal/reclamos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class PortalReclamoController {

    private final ReclamoService reclamoService;

    @GetMapping
    public ResponseEntity<List<ReclamoPortalResponse>> listar() {
        return ResponseEntity.ok(reclamoService.listarDelPortal());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReclamoPortalResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(reclamoService.detalleDelPortal(id));
    }

    @PostMapping
    public ResponseEntity<ReclamoPortalResponse> crear(@Valid @RequestBody ReclamoPortalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reclamoService.crearDesdePortal(request));
    }

    @PostMapping("/{id}/calificacion")
    public ResponseEntity<ReclamoPortalResponse> calificar(@PathVariable Long id,
                                                           @Valid @RequestBody CalificacionReclamoRequest request) {
        return ResponseEntity.ok(reclamoService.calificarDesdePortal(id, request));
    }
}
