package com.genlogs.app.controller;

import com.genlogs.app.model.EmpresaMinera;
import com.genlogs.app.service.EmpresaMineraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas-mineras")
@RequiredArgsConstructor
public class EmpresaMineraController {

    private final EmpresaMineraService empresaMineraService;

    @GetMapping
    public ResponseEntity<List<EmpresaMinera>> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
        List<EmpresaMinera> resultado = soloActivos
                ? empresaMineraService.listarActivos()
                : empresaMineraService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaMinera> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(empresaMineraService.buscarPorId(id));
    }

    @GetMapping("/por-cliente/{idCliente}")
    public ResponseEntity<EmpresaMinera> buscarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(empresaMineraService.buscarPorCliente(idCliente));
    }

    @PostMapping
    public ResponseEntity<EmpresaMinera> registrar(@Valid @RequestBody EmpresaMinera nueva) {
        EmpresaMinera creada = empresaMineraService.registrar(nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaMinera> actualizar(@PathVariable Long id,
                                                     @Valid @RequestBody EmpresaMinera datos) {
        return ResponseEntity.ok(empresaMineraService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        empresaMineraService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
