package com.genlogs.app.controller;

import com.genlogs.app.model.EtapaComercialMinera;
import com.genlogs.app.service.EtapaComercialMineraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/etapas-comerciales-minera")
@RequiredArgsConstructor
public class EtapaComercialMineraController {

    private final EtapaComercialMineraService etapaComercialMineraService;

    @GetMapping
    public ResponseEntity<List<EtapaComercialMinera>> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
        List<EtapaComercialMinera> resultado = soloActivos
                ? etapaComercialMineraService.listarActivos()
                : etapaComercialMineraService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EtapaComercialMinera> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(etapaComercialMineraService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<EtapaComercialMinera> registrar(@Valid @RequestBody EtapaComercialMinera nueva) {
        EtapaComercialMinera creada = etapaComercialMineraService.registrar(nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EtapaComercialMinera> actualizar(@PathVariable Integer id,
                                                            @Valid @RequestBody EtapaComercialMinera datos) {
        return ResponseEntity.ok(etapaComercialMineraService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        etapaComercialMineraService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
