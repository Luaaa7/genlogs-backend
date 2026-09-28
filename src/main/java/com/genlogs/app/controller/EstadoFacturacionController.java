package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.EstadoFacturacionResponse;
import com.genlogs.app.service.EstadoFacturacionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/estados-facturacion")
@RequiredArgsConstructor
public class EstadoFacturacionController {

    private final EstadoFacturacionService estadoFacturacionService;

    @GetMapping
    public ResponseEntity<List<EstadoFacturacionResponse>> listarTodos() {
        return ResponseEntity.ok(estadoFacturacionService.listarTodos());
    }
}
