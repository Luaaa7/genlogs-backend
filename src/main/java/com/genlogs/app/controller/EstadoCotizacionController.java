package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.EstadoCotizacionResponse;
import com.genlogs.app.service.EstadoCotizacionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/estados-cotizacion")
@RequiredArgsConstructor
public class EstadoCotizacionController {

    private final EstadoCotizacionService estadoCotizacionService;

    @GetMapping
    public ResponseEntity<List<EstadoCotizacionResponse>> listar() {
        return ResponseEntity.ok(estadoCotizacionService.listarTodos());
    }
}