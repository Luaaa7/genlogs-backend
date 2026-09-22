package com.genlogs.app.controller;

import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.service.CondicionPagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/condiciones-pago")
@RequiredArgsConstructor
public class CondicionPagoController {

    private final CondicionPagoService condicionPagoService;

    @GetMapping
    public ResponseEntity<List<CondicionPago>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        List<CondicionPago> resultado = soloActivos
                ? condicionPagoService.listarActivos()
                : condicionPagoService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CondicionPago> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(condicionPagoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CondicionPago> registrar(@Valid @RequestBody CondicionPago nueva) {
        CondicionPago creada = condicionPagoService.registrar(nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CondicionPago> actualizar(@PathVariable Integer id,
                                                     @Valid @RequestBody CondicionPago datos) {
        return ResponseEntity.ok(condicionPagoService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        condicionPagoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
