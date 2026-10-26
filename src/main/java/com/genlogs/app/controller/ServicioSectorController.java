package com.genlogs.app.controller;

import com.genlogs.app.model.ServicioSector;
import com.genlogs.app.service.ServicioSectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Rutas anidadas bajo /api/servicios/{idServicio}/sectores */
@RestController
@RequestMapping("/api/servicios/{idServicio}/sectores")
@RequiredArgsConstructor
public class ServicioSectorController {

    private final ServicioSectorService servicioSectorService;

    @GetMapping
    public ResponseEntity<List<ServicioSector>> listar(@PathVariable Long idServicio) {
        return ResponseEntity.ok(servicioSectorService.listarPorServicio(idServicio));
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/{idSectorEconomico}")
    public ResponseEntity<ServicioSector> asociar(@PathVariable Long idServicio,
                                                   @PathVariable Integer idSectorEconomico) {
        ServicioSector creada = servicioSectorService.asociar(idServicio, idSectorEconomico);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{idSectorEconomico}")
    public ResponseEntity<Void> desasociar(@PathVariable Long idServicio,
                                            @PathVariable Integer idSectorEconomico) {
        servicioSectorService.desasociar(idServicio, idSectorEconomico);
        return ResponseEntity.noContent().build();
    }
}