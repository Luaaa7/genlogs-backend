package com.genlogs.app.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.AvanzarEtapaDespachoRequest;
import com.genlogs.app.dto.CrearDespachoRequest;
import com.genlogs.app.dto.DespachoResponse;
import com.genlogs.app.service.DespachoService;
import com.genlogs.app.service.PermisosDespacho;
import com.genlogs.app.service.PlazoDespacho;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/despachos")
@RequiredArgsConstructor
public class DespachoController {

    private final DespachoService despachoService;

    @PreAuthorize(PermisosDespacho.VER)
    @GetMapping
    public ResponseEntity<List<DespachoResponse>> listar() {
        return ResponseEntity.ok(despachoService.listar());
    }

    @PreAuthorize(PermisosDespacho.VER)
    @GetMapping("/{id}")
    public ResponseEntity<DespachoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(despachoService.buscar(id));
    }

    @PreAuthorize(PermisosDespacho.GESTIONAR)
    @PostMapping
    public ResponseEntity<DespachoResponse> crear(@Valid @RequestBody CrearDespachoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                despachoService.crear(request.getIdOrdenCompra(), LocalDateTime.now(PlazoDespacho.LIMA)));
    }

    @PreAuthorize(PermisosDespacho.GESTIONAR)
    @PatchMapping("/{id}/etapa")
    public ResponseEntity<DespachoResponse> avanzar(
            @PathVariable Long id, @Valid @RequestBody AvanzarEtapaDespachoRequest request) {
        return ResponseEntity.ok(despachoService.avanzar(id, request.getEtapa()));
    }
}
