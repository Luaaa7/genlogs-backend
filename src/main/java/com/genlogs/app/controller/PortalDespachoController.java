package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.PortalDespachoResponse;
import com.genlogs.app.service.PermisosDespacho;
import com.genlogs.app.service.PortalDespachoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/portal/despachos")
@PreAuthorize(PermisosDespacho.PORTAL)
@RequiredArgsConstructor
public class PortalDespachoController {

    private final PortalDespachoService portalDespachoService;

    @GetMapping
    public ResponseEntity<List<PortalDespachoResponse>> listar() {
        return ResponseEntity.ok(portalDespachoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortalDespachoResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(portalDespachoService.detalle(id));
    }
}
