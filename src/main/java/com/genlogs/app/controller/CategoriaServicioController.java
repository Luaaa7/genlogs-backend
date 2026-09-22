package com.genlogs.app.controller;

import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.service.CategoriaServicioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/categorias-servicio")
@RequiredArgsConstructor
public class CategoriaServicioController {

    private final CategoriaServicioService categoriaServicioService;

    @GetMapping
    public ResponseEntity<List<CategoriaServicio>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        List<CategoriaServicio> resultado = soloActivos
                ? categoriaServicioService.listarActivos()
                : categoriaServicioService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaServicio> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(categoriaServicioService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CategoriaServicio> registrar(@Valid @RequestBody CategoriaServicio nueva) {
        CategoriaServicio creada = categoriaServicioService.registrar(nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaServicio> actualizar(@PathVariable Integer id,
                                                         @Valid @RequestBody CategoriaServicio datos) {
        return ResponseEntity.ok(categoriaServicioService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        categoriaServicioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
