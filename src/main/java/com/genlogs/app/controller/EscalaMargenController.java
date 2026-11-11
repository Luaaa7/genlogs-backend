package com.genlogs.app.controller;

import com.genlogs.app.dto.EscalaMargenDto;
import com.genlogs.app.service.EscalaMargenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Escala de márgenes. Va en /api/configuracion (y no en /api/catalogos) porque
 * los GET de /api/catalogos son públicos y los márgenes no deben verse sin sesión.
 */
@RestController
@RequestMapping("/api/configuracion/margenes")
@RequiredArgsConstructor
public class EscalaMargenController {

    private final EscalaMargenService escalaMargenService;

    /** Cualquier usuario con sesión: el formulario de cotización la usa para sugerir el margen. */
    @GetMapping
    public ResponseEntity<EscalaMargenDto.Escala> obtener() {
        return ResponseEntity.ok(escalaMargenService.obtener());
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping
    public ResponseEntity<EscalaMargenDto.Escala> guardar(@Valid @RequestBody EscalaMargenDto.Escala escala) {
        return ResponseEntity.ok(escalaMargenService.guardar(escala));
    }
}
