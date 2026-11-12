package com.genlogs.app.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.model.Feriado;
import com.genlogs.app.service.FeriadoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Feriados (D-18). Todo el personal interno los ve (el calendario de plazos los usa);
 * solo el Jefe Administrativo los agrega o da de baja.
 */
@RestController
@RequestMapping("/api/feriados")
@RequiredArgsConstructor
public class FeriadoController {

    private final FeriadoService feriadoService;

    public record FeriadoRequest(
            @NotNull(message = "Indica la fecha del feriado")
            LocalDate fecha,
            @NotBlank(message = "Indica el motivo del feriado")
            @Size(max = 100, message = "El motivo no puede pasar de 100 caracteres")
            String descripcion) {}

    /** Feriados activos desde una fecha (por defecto, hoy). */
    @GetMapping
    public ResponseEntity<List<Feriado>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return ResponseEntity.ok(feriadoService.listarDesde(desde));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_ADMINISTRATIVO')")
    @PostMapping
    public ResponseEntity<Feriado> crear(@Valid @RequestBody FeriadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feriadoService.crear(request.fecha(), request.descripcion()));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_ADMINISTRATIVO')")
    @PatchMapping("/{idFeriado}/baja")
    public ResponseEntity<Void> darDeBaja(@PathVariable Integer idFeriado) {
        feriadoService.darDeBaja(idFeriado);
        return ResponseEntity.noContent().build();
    }
}
