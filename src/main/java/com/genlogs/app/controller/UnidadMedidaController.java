package com.genlogs.app.controller;

import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.service.UnidadMedidaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/unidades-medida")
@RequiredArgsConstructor
public class UnidadMedidaController {

    private final UnidadMedidaService unidadMedidaService;

    @GetMapping
    public ResponseEntity<List<UnidadMedida>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        List<UnidadMedida> resultado = soloActivos
                ? unidadMedidaService.listarActivos()
                : unidadMedidaService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnidadMedida> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(unidadMedidaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<UnidadMedida> registrar(@Valid @RequestBody UnidadMedida nueva) {
        UnidadMedida creada = unidadMedidaService.registrar(nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnidadMedida> actualizar(@PathVariable Integer id,
                                                    @Valid @RequestBody UnidadMedida datos) {
        return ResponseEntity.ok(unidadMedidaService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        unidadMedidaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
