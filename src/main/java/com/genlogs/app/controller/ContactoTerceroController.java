package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ContactoTerceroRequest;
import com.genlogs.app.dto.ContactoTerceroResponse;
import com.genlogs.app.service.ContactoTerceroService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ContactoTerceroController {

    private final ContactoTerceroService contactoTerceroService;

    @GetMapping("/api/terceros/{idTercero}/contactos")
    public ResponseEntity<List<ContactoTerceroResponse>> listar(@PathVariable Long idTercero) {
        return ResponseEntity.ok(contactoTerceroService.listarPorTercero(idTercero));
    }

    @PostMapping("/api/terceros/{idTercero}/contactos")
    public ResponseEntity<ContactoTerceroResponse> crear(
            @PathVariable Long idTercero, @Valid @RequestBody ContactoTerceroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contactoTerceroService.crear(idTercero, request));
    }

    @PutMapping("/api/contactos/{idContacto}")
    public ResponseEntity<ContactoTerceroResponse> actualizar(
            @PathVariable Long idContacto, @Valid @RequestBody ContactoTerceroRequest request) {
        return ResponseEntity.ok(contactoTerceroService.actualizar(idContacto, request));
    }

    @DeleteMapping("/api/contactos/{idContacto}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idContacto) {
        contactoTerceroService.eliminar(idContacto);
        return ResponseEntity.noContent().build();
    }
}