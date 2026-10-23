package com.genlogs.app.controller;

import com.genlogs.app.model.ServicioImagen;
import com.genlogs.app.service.ServicioImagenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Void> subirImagen(@PathVariable Long idServicio,
                                           @RequestParam("archivo") MultipartFile archivo,
                                           @RequestParam(defaultValue = "false") boolean esPrincipal) {
        servicioImagenService.subirImagen(idServicio, archivo, esPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping(consumes = "application/json")
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
