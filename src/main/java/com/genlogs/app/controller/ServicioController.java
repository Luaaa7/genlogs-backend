package com.genlogs.app.controller;

import com.genlogs.app.dto.ServicioResponse;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.service.ServicioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * S2 (matriz v3.2): ven los servicios Gerente General y Asistente Administrativo; editan
 * Jefe Administrativo y Asistente Logístico. Contador y Supervisor SST no entran.
 * Las reglas de cada método (registrar, aprobar...) reemplazan a la de la clase.
 */
@RestController
@RequestMapping("/api/servicios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GERENTE_GENERAL','JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO','ASISTENTE_ADMINISTRATIVO')")
public class ServicioController {

    private final ServicioService servicioService;

    @GetMapping
    public ResponseEntity<List<ServicioResponse>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @RequestParam(required = false) Integer idCategoriaServicio) {
        List<Servicio> resultado;
        if (idCategoriaServicio != null) {
            resultado = servicioService.listarPorCategoria(idCategoriaServicio);
        } else {
            resultado = soloActivos ? servicioService.listarActivos() : servicioService.listarTodos();
        }
        boolean veCosto = veCostoYMargen();
        return ResponseEntity.ok(resultado.stream().map(s -> ServicioResponse.de(s, veCosto)).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicioResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ServicioResponse.de(servicioService.buscarPorId(id), veCostoYMargen()));
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    @PostMapping
    public ResponseEntity<ServicioResponse> registrar(@Valid @RequestBody Servicio nuevo) {
        Servicio creado = servicioService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServicioResponse.de(creado, veCostoYMargen()));
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    @PutMapping("/{id}")
    public ResponseEntity<ServicioResponse> actualizar(@PathVariable Long id,
                                                        @Valid @RequestBody Servicio datos) {
        return ResponseEntity.ok(ServicioResponse.de(servicioService.actualizar(id, datos), veCostoYMargen()));
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        servicioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PostMapping("/{id}/aprobar")
    public ResponseEntity<ServicioResponse> aprobar(@PathVariable Long id) {
        return ResponseEntity.ok(ServicioResponse.de(servicioService.aprobar(id), veCostoYMargen()));
    }

    /** Solo el Jefe Administrativo, y no sobre un servicio que él mismo dejó pendiente. */
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PostMapping("/{id}/rechazar")
    public ResponseEntity<ServicioResponse> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(ServicioResponse.de(servicioService.rechazar(id), veCostoYMargen()));
    }

    // ------------------------------------------------------------------------
    // Costo y margen son datos internos (matriz v3.2: márgenes solo para Gerente
    // General, Jefe Administrativo y Asistente Logístico). Los demás roles ven el
    // servicio con su precio referencial, pero sin costo ni margen (ServicioResponse).
    // ------------------------------------------------------------------------

    static final Set<String> ROLES_CON_COSTO = Set.of(
            "ROLE_GERENTE_GENERAL", "ROLE_JEFE_ADMINISTRATIVO", "ROLE_ASISTENTE_LOGISTICO");

    static boolean veCostoYMargen() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_CON_COSTO::contains);
    }
}