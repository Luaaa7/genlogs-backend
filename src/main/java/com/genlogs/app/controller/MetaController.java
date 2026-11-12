package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.AccionCorrectivaResponse;
import com.genlogs.app.dto.ActualizarMetaRequest;
import com.genlogs.app.dto.CambiarEstadoAccionRequest;
import com.genlogs.app.dto.CrearAccionRequest;
import com.genlogs.app.dto.MetaPeriodoResponse;
import com.genlogs.app.service.MetasPeriodoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Metas de cotizaciones en plazo. Cualquier rol interno consulta.
 * Solo el Jefe Administrativo fija la meta y registra acciones.
 */
@RestController
@RequestMapping("/api/metas")
@RequiredArgsConstructor
public class MetaController {

    private final MetasPeriodoService metasPeriodoService;

    @GetMapping
    public ResponseEntity<MetaPeriodoResponse> consultar(@RequestParam int anio, @RequestParam int mes) {
        return ResponseEntity.ok(metasPeriodoService.consultar(anio, mes));
    }

    /** Historial de resultados: los últimos {@code meses} periodos hasta anio/mes (por defecto 6). */
    @GetMapping("/historial")
    public ResponseEntity<List<MetaPeriodoResponse>> historial(@RequestParam int anio, @RequestParam int mes,
            @RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(metasPeriodoService.historial(anio, mes, meses));
    }

    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PutMapping("/{anio}/{mes}")
    public ResponseEntity<MetaPeriodoResponse> actualizar(@PathVariable int anio, @PathVariable int mes,
            @Valid @RequestBody ActualizarMetaRequest request) {
        return ResponseEntity.ok(metasPeriodoService.actualizarMeta(anio, mes, request.metaPorcentaje()));
    }

    @GetMapping("/{anio}/{mes}/acciones")
    public ResponseEntity<List<AccionCorrectivaResponse>> listarAcciones(@PathVariable int anio,
            @PathVariable int mes) {
        return ResponseEntity.ok(metasPeriodoService.listarAcciones(anio, mes));
    }

    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PostMapping("/{anio}/{mes}/acciones")
    public ResponseEntity<AccionCorrectivaResponse> registrarAccion(@PathVariable int anio, @PathVariable int mes,
            @Valid @RequestBody CrearAccionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(metasPeriodoService.registrarAccion(anio, mes, request.descripcion()));
    }

    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    @PatchMapping("/acciones/{id}")
    public ResponseEntity<AccionCorrectivaResponse> cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambiarEstadoAccionRequest request) {
        return ResponseEntity.ok(metasPeriodoService.cambiarEstado(id, request.estado()));
    }
}
