package com.genlogs.app.controller;

import com.genlogs.app.model.Moneda;
import com.genlogs.app.service.MonedaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/monedas")
@RequiredArgsConstructor
public class MonedaController {

    private final MonedaService monedaService;

    @GetMapping
    public ResponseEntity<List<Moneda>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        List<Moneda> resultado = soloActivos
                ? monedaService.listarActivos()
                : monedaService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Moneda> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(monedaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Moneda> registrar(@Valid @RequestBody Moneda nueva) {
        Moneda creada = monedaService.registrar(nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Moneda> actualizar(@PathVariable Integer id,
                                              @Valid @RequestBody Moneda datos) {
        return ResponseEntity.ok(monedaService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        monedaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
