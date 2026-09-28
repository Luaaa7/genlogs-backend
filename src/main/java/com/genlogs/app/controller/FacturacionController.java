package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CambiarEstadoFacturacionRequest;
import com.genlogs.app.dto.DetalleFacturacionResponse;
import com.genlogs.app.dto.FacturacionRequest;
import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.dto.RegistrarPagoRequest;
import com.genlogs.app.service.FacturacionService;

import jakarta.validation.Valid;
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

    @GetMapping("/{id}/detalle")
    public ResponseEntity<List<DetalleFacturacionResponse>> listarDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(facturacionService.listarDetalle(id));
    }

    @PostMapping
    public ResponseEntity<FacturacionResponse> crear(@Valid @RequestBody FacturacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturacionService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<FacturacionResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody CambiarEstadoFacturacionRequest request) {
        return ResponseEntity.ok(facturacionService.cambiarEstado(id, request));
    }

    @PatchMapping("/{id}/pago")
    public ResponseEntity<FacturacionResponse> registrarPago(
            @PathVariable Long id, @Valid @RequestBody RegistrarPagoRequest request) {
        return ResponseEntity.ok(facturacionService.registrarPago(id, request));
    }
}
