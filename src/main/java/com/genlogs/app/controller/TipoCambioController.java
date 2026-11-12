package com.genlogs.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CorregirTipoCambioRequest;
import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.service.ConsultaTipoCambioService;
import com.genlogs.app.service.TipoCambioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tipo de cambio del dólar (RF-86). Cualquier rol interno lo ve.
 * Solo el Jefe Administrativo lo corrige; el filtro de URL no se toca.
 */
@RestController
@RequestMapping("/api/tipo-cambio")
@RequiredArgsConstructor
public class TipoCambioController {

    private final TipoCambioService tipoCambioService;
    private final ConsultaTipoCambioService consultaTipoCambioService;

    @GetMapping
    public ResponseEntity<TipoCambioDia> hoy() {
        return ResponseEntity.of(tipoCambioService.delDia());
    }

    @PutMapping
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    public ResponseEntity<TipoCambioDia> corregir(@Valid @RequestBody CorregirTipoCambioRequest request) {
        return ResponseEntity.ok(consultaTipoCambioService.corregirHoy(request.valor()));
    }
}
