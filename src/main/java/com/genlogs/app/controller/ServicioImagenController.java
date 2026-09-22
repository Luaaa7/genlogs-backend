package com.genlogs.app.controller;

import com.genlogs.app.model.ServicioImagen;
import com.genlogs.app.service.ServicioImagenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Rutas anidadas bajo /api/servicios/{idServicio}/imagenes */
@RestController
@RequestMapping("/api/servicios/{idServicio}/imagenes")
@RequiredArgsConstructor
public class ServicioImagenController {

    private final ServicioImagenService servicioImagenService;

    @GetMapping
    public ResponseEntity<List<ServicioImagen>> listar(@PathVariable Long idServicio) {
        return ResponseEntity.ok(servicioImagenService.listarPorServicio(idServicio));
    }

    @PostMapping
    public ResponseEntity<ServicioImagen> registrar(@PathVariable Long idServicio,
                                                     @Valid @RequestBody ServicioImagen nueva) {
        ServicioImagen creada = servicioImagenService.registrar(idServicio, nueva);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @DeleteMapping("/{idServicioImagen}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idServicio,
                                          @PathVariable Long idServicioImagen) {
        servicioImagenService.eliminar(idServicioImagen);
        return ResponseEntity.noContent().build();
    }
}
