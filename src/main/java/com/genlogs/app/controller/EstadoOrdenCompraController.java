package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.EstadoOrdenCompraResponse;
import com.genlogs.app.service.EstadoOrdenCompraService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/estados-orden-compra")
@RequiredArgsConstructor
public class EstadoOrdenCompraController {

    private final EstadoOrdenCompraService estadoOrdenCompraService;

    @GetMapping
    public ResponseEntity<List<EstadoOrdenCompraResponse>> listarTodos() {
        return ResponseEntity.ok(estadoOrdenCompraService.listarTodos());
    }
}
