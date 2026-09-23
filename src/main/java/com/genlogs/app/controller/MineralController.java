package com.genlogs.app.controller;

import com.genlogs.app.model.Mineral;
import com.genlogs.app.service.MineralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/minerales")
@RequiredArgsConstructor
public class MineralController {

    private final MineralService mineralService;

    @GetMapping
    public ResponseEntity<List<Mineral>> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
        List<Mineral> resultado = soloActivos ? mineralService.listarActivos() : mineralService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mineral> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(mineralService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Mineral> registrar(@Valid @RequestBody Mineral nuevo) {
        Mineral creado = mineralService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Mineral> actualizar(@PathVariable Integer id, @Valid @RequestBody Mineral datos) {
        return ResponseEntity.ok(mineralService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        mineralService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
