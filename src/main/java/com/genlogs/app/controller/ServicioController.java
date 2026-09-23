package com.genlogs.app.controller;

import com.genlogs.app.model.Servicio;
import com.genlogs.app.service.ServicioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Servicio> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(servicioService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Servicio> registrar(@Valid @RequestBody Servicio nuevo) {
        Servicio creado = servicioService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Servicio> actualizar(@PathVariable Long id,
                                                @Valid @RequestBody Servicio datos) {
        return ResponseEntity.ok(servicioService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        servicioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
