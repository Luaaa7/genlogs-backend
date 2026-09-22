package com.genlogs.app.controller;

import com.genlogs.app.model.TipoOperacionMinera;
import com.genlogs.app.service.TipoOperacionMineraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/tipos-operacion-minera")
@RequiredArgsConstructor
public class TipoOperacionMineraController {

    private final TipoOperacionMineraService tipoOperacionMineraService;

    @GetMapping
    public ResponseEntity<List<TipoOperacionMinera>> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
        List<TipoOperacionMinera> resultado = soloActivos
                ? tipoOperacionMineraService.listarActivos()
                : tipoOperacionMineraService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoOperacionMinera> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(tipoOperacionMineraService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<TipoOperacionMinera> registrar(@Valid @RequestBody TipoOperacionMinera nuevo) {
        TipoOperacionMinera creado = tipoOperacionMineraService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoOperacionMinera> actualizar(@PathVariable Integer id,
                                                           @Valid @RequestBody TipoOperacionMinera datos) {
        return ResponseEntity.ok(tipoOperacionMineraService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        tipoOperacionMineraService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
