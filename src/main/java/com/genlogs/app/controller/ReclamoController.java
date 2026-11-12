package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CambiarEstadoReclamoRequest;
import com.genlogs.app.dto.ComentarioReclamoRequest;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.dto.ReclamoRequest;
import com.genlogs.app.dto.ReclamoResponse;
import com.genlogs.app.dto.SeguimientoReclamoResponse;
import com.genlogs.app.model.EstadoReclamo;
import com.genlogs.app.model.PrioridadReclamo;
import com.genlogs.app.model.TipoReclamo;
import com.genlogs.app.service.ReclamoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Reclamos e incidencias: registro y seguimiento. */
@RestController
@RequestMapping("/api/reclamos")
@RequiredArgsConstructor
public class ReclamoController {

    private final ReclamoService reclamoService;

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ASISTENTE_ADMINISTRATIVO','ADMINISTRADOR','VENDEDOR')")
    @GetMapping
    public ResponseEntity<PaginaResponse<ReclamoResponse>> listar(
            @RequestParam(required = false) TipoReclamo tipo,
            @RequestParam(required = false) EstadoReclamo estado,
            @RequestParam(required = false) PrioridadReclamo prioridad,
            @RequestParam(required = false) Long idCliente,
            @RequestParam(required = false) Long idResponsable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reclamoService.listar(tipo, estado, prioridad, idCliente, idResponsable, page, size));
    }

    /** Usuarios activos para asignar como responsables (GET /api/usuarios es solo del Jefe Administrativo y el Gerente). */
    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ASISTENTE_ADMINISTRATIVO','ADMINISTRADOR','VENDEDOR')")
    @GetMapping("/responsables")
    public ResponseEntity<List<java.util.Map<String, Object>>> listarResponsables() {
        return ResponseEntity.ok(reclamoService.listarResponsables());
    }

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ASISTENTE_ADMINISTRATIVO','ADMINISTRADOR','VENDEDOR')")
    @GetMapping("/{id}")
    public ResponseEntity<ReclamoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(reclamoService.buscarPorId(id));
    }

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ASISTENTE_ADMINISTRATIVO','ADMINISTRADOR','VENDEDOR')")
    @GetMapping("/{id}/seguimientos")
    public ResponseEntity<List<SeguimientoReclamoResponse>> listarSeguimientos(@PathVariable Long id) {
        return ResponseEntity.ok(reclamoService.listarSeguimientos(id));
    }

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ADMINISTRADOR','VENDEDOR')")
    @PostMapping
    public ResponseEntity<ReclamoResponse> crear(@Valid @RequestBody ReclamoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reclamoService.crear(request));
    }

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ADMINISTRADOR','VENDEDOR')")
    @PutMapping("/{id}")
    public ResponseEntity<ReclamoResponse> actualizar(@PathVariable Long id,
                                                      @Valid @RequestBody ReclamoRequest request) {
        return ResponseEntity.ok(reclamoService.actualizar(id, request));
    }

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ADMINISTRADOR','VENDEDOR')")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<ReclamoResponse> cambiarEstado(@PathVariable Long id,
                                                         @Valid @RequestBody CambiarEstadoReclamoRequest request) {
        return ResponseEntity.ok(reclamoService.cambiarEstado(id, request));
    }

    @PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ADMINISTRADOR','VENDEDOR')")
    @PostMapping("/{id}/seguimientos")
    public ResponseEntity<SeguimientoReclamoResponse> agregarComentario(@PathVariable Long id,
                                                                        @Valid @RequestBody ComentarioReclamoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reclamoService.agregarComentario(id, request));
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        reclamoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
