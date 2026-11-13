package com.genlogs.app.controller;

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

import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/servicios")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    @GetMapping
    public ResponseEntity<List<Servicio>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @RequestParam(required = false) Integer idCategoriaServicio) {
        List<Servicio> resultado;
        if (idCategoriaServicio != null) {
            resultado = servicioService.listarPorCategoria(idCategoriaServicio);
        } else {
            resultado = soloActivos ? servicioService.listarActivos() : servicioService.listarTodos();
        }
        boolean veCosto = veCostoYMargen();
        return ResponseEntity.ok(resultado.stream().map(s -> paraElRol(s, veCosto)).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Servicio> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(paraElRol(servicioService.buscarPorId(id), veCostoYMargen()));
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    @PostMapping
    public ResponseEntity<Servicio> registrar(@Valid @RequestBody Servicio nuevo) {
        Servicio creado = servicioService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    @PutMapping("/{id}")
    public ResponseEntity<Servicio> actualizar(@PathVariable Long id,
                                                @Valid @RequestBody Servicio datos) {
        return ResponseEntity.ok(servicioService.actualizar(id, datos));
    }

    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        servicioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PostMapping("/{id}/aprobar")
    public ResponseEntity<Servicio> aprobar(@PathVariable Long id) {
        return ResponseEntity.ok(servicioService.aprobar(id));
    }

    /** Solo el Jefe Administrativo, y no sobre un servicio que él mismo dejó pendiente. */
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Servicio> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(servicioService.rechazar(id));
    }

    // ------------------------------------------------------------------------
    // Costo y margen son datos internos (matriz v3.2: márgenes solo para Gerente
    // General, Jefe Administrativo y Asistente Logístico). Los demás roles ven el
    // servicio con su precio referencial, pero sin costo ni margen.
    // ------------------------------------------------------------------------

    static final Set<String> ROLES_CON_COSTO = Set.of(
            "ROLE_GERENTE_GENERAL", "ROLE_JEFE_ADMINISTRATIVO", "ROLE_ASISTENTE_LOGISTICO");

    static boolean veCostoYMargen() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_CON_COSTO::contains);
    }

    /** Copia sin costo ni margen para quien no puede verlos (no toca la entidad guardada). */
    static Servicio paraElRol(Servicio servicio, boolean veCosto) {
        if (servicio == null || veCosto) {
            return servicio;
        }
        Servicio copia = new Servicio();
        BeanUtils.copyProperties(servicio, copia);
        copia.setCosto(null);
        copia.setMargen(null);
        return copia;
    }
}