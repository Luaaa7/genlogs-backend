package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CoberturaPublicaResponse;
import com.genlogs.app.dto.ProductoPublicoResponse;
import com.genlogs.app.service.PortalClienteService;

import lombok.RequiredArgsConstructor;

/** Landing pública. No lleva token y no devuelve datos internos. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PortalClienteService portalClienteService;

    @GetMapping("/catalogo")
    public ResponseEntity<List<ProductoPublicoResponse>> catalogo() {
        return ResponseEntity.ok(portalClienteService.catalogo());
    }

    @GetMapping("/cobertura")
    public ResponseEntity<List<CoberturaPublicaResponse>> cobertura() {
        return ResponseEntity.ok(portalClienteService.cobertura());
    }
}
