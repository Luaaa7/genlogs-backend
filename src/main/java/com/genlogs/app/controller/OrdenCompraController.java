package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.OrdenCompraResponse;
import com.genlogs.app.service.OrdenCompraService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordenes-compra")
@RequiredArgsConstructor
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    @GetMapping("/{id}")
    public ResponseEntity<OrdenCompraResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordenCompraService.buscarPorId(id));
    }

    @GetMapping("/numero/{numero}")
    public ResponseEntity<OrdenCompraResponse> buscarPorNumero(@PathVariable String numero) {
        return ResponseEntity.ok(ordenCompraService.buscarPorNumero(numero));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<OrdenCompraResponse>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(ordenCompraService.listarPorCliente(idCliente));
    }
}
