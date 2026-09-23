package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.service.CotizacionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizacionService;

    @GetMapping("/{id}")
    public ResponseEntity<CotizacionResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.buscarPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<CotizacionResponse> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(cotizacionService.buscarPorCodigo(codigo));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<CotizacionResponse>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(cotizacionService.listarPorCliente(idCliente));
    }
}
