package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.service.FacturacionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/facturacion")
@RequiredArgsConstructor
public class FacturacionController {

    private final FacturacionService facturacionService;

    @GetMapping("/{id}")
    public ResponseEntity<FacturacionResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(facturacionService.buscarPorId(id));
    }

    @GetMapping("/comprobante/{serie}/{numero}")
    public ResponseEntity<FacturacionResponse> buscarPorComprobante(@PathVariable String serie, @PathVariable String numero) {
        return ResponseEntity.ok(facturacionService.buscarPorComprobante(serie, numero));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<FacturacionResponse>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(facturacionService.listarPorCliente(idCliente));
    }
}
