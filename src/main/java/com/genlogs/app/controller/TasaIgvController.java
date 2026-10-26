package com.genlogs.app.controller;

import com.genlogs.app.model.TasaIgv;
import com.genlogs.app.service.TasaIgvService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/catalogos/igv")
@RequiredArgsConstructor
public class TasaIgvController {

    private final TasaIgvService tasaIgvService;

    public record TasaIgvVigenteResponse(BigDecimal porcentaje) {}

    public record TasaIgvRequest(
            @NotNull(message = "Indica el porcentaje")
            @DecimalMin(value = "0.00", message = "El porcentaje no puede ser negativo")
            @DecimalMax(value = "99.99", message = "El porcentaje debe ser menor a 100")
            BigDecimal porcentaje,
            @NotNull(message = "Indica desde qué fecha rige")
            LocalDate vigenteDesde) {}

    /** Tasa que rige hoy, por ejemplo { "porcentaje": 18.00 }. */
    @GetMapping
    public ResponseEntity<TasaIgvVigenteResponse> vigente() {
        return ResponseEntity.ok(new TasaIgvVigenteResponse(tasaIgvService.porcentajeVigente()));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<TasaIgv>> historial() {
        return ResponseEntity.ok(tasaIgvService.historial());
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<TasaIgv> registrar(@Valid @RequestBody TasaIgvRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tasaIgvService.registrar(request.porcentaje(), request.vigenteDesde()));
    }
}