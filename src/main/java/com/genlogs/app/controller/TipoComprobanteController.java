package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.TipoComprobanteResponse;
import com.genlogs.app.service.TipoComprobanteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tipos-comprobante")
@RequiredArgsConstructor
public class TipoComprobanteController {

    private final TipoComprobanteService tipoComprobanteService;

    @GetMapping
    public ResponseEntity<List<TipoComprobanteResponse>> listarTodos() {
        return ResponseEntity.ok(tipoComprobanteService.listarTodos());
    }
}
