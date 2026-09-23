package com.genlogs.app.controller;

import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.service.SectorEconomicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos/sectores-economicos")
@RequiredArgsConstructor
public class SectorEconomicoController {

    private final SectorEconomicoService sectorEconomicoService;

    @GetMapping
    public ResponseEntity<List<SectorEconomico>> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        List<SectorEconomico> resultado = soloActivos
                ? sectorEconomicoService.listarActivos()
                : sectorEconomicoService.listarTodos();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectorEconomico> buscarPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(sectorEconomicoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<SectorEconomico> registrar(@Valid @RequestBody SectorEconomico nuevo) {
        SectorEconomico creado = sectorEconomicoService.registrar(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SectorEconomico> actualizar(@PathVariable Integer id,
                                                       @Valid @RequestBody SectorEconomico datos) {
        return ResponseEntity.ok(sectorEconomicoService.actualizar(id, datos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        sectorEconomicoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
