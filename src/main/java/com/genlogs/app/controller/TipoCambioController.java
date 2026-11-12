package com.genlogs.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CorregirTipoCambioRequest;
import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.service.TipoCambioDiarioService;
import com.genlogs.app.service.TipoCambioService;

import jakarta.validation.Valid;

/**
 * Tipo de cambio del día (BE-D). Lo ve todo el personal interno (regla de SecurityConfig);
 * solo el Jefe Administrativo lo corrige o pide consultarlo de nuevo.
 *
 * GET  /api/tipo-cambio            -> TipoCambioDia (404 si nunca se guardó uno)
 * PUT  /api/tipo-cambio            -> corrige el de hoy (MANUAL), {valor} entre 1 y 10
 * POST /api/tipo-cambio/consultar  -> vuelve a consultar Factiliza ahora
 */
@RestController
@RequestMapping("/api/tipo-cambio")
public class TipoCambioController {

    private final TipoCambioService tipoCambioService;
    private final TipoCambioDiarioService diario;

    public TipoCambioController(TipoCambioService tipoCambioService, TipoCambioDiarioService diario) {
        this.tipoCambioService = tipoCambioService;
        this.diario = diario;
    }

    @GetMapping
    public ResponseEntity<TipoCambioDia> delDia() {
        return ResponseEntity.ok(tipoCambioService.delDia()
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no hay tipo de cambio guardado")));
    }

    @PutMapping
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    public ResponseEntity<TipoCambioDia> corregir(@Valid @RequestBody CorregirTipoCambioRequest request) {
        return ResponseEntity.ok(diario.corregirHoy(request.valor()));
    }

    @PostMapping("/consultar")
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    public ResponseEntity<TipoCambioDia> consultar() {
        return ResponseEntity.ok(diario.consultarAhora());
    }
}
