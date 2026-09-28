package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.AdjuntoCotizacionResponse;
import com.genlogs.app.dto.CambiarEstadoCotizacionRequest;
import com.genlogs.app.dto.CotizacionDetalleResponse;
import com.genlogs.app.dto.CotizacionRequest;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.SeguimientoCotizacionResponse;
import com.genlogs.app.service.CotizacionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizacionService;

    @GetMapping("/{id}")
    public ResponseEntity<CotizacionResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.buscarPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<CotizacionResponse> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(cotizacionService.buscarPorCodigo(codigo));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<CotizacionResponse>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(cotizacionService.listarPorCliente(idCliente));
    }

    @GetMapping("/{id}/lineas")
    public ResponseEntity<List<CotizacionDetalleResponse>> listarLineas(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.listarLineas(id));
    }

    @GetMapping("/{id}/seguimiento")
    public ResponseEntity<List<SeguimientoCotizacionResponse>> listarSeguimiento(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.listarSeguimiento(id));
    }

    @GetMapping("/{id}/adjuntos")
    public ResponseEntity<List<AdjuntoCotizacionResponse>> listarAdjuntos(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.listarAdjuntos(id));
    }

    @PostMapping
    public ResponseEntity<CotizacionResponse> crear(@Valid @RequestBody CotizacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotizacionService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<CotizacionResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody CambiarEstadoCotizacionRequest request) {
        return ResponseEntity.ok(cotizacionService.cambiarEstado(id, request));
    }

    @PostMapping(value = "/{id}/adjuntos", consumes = "multipart/form-data")
    public ResponseEntity<AdjuntoCotizacionResponse> agregarAdjunto(
            @PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotizacionService.agregarAdjunto(id, archivo));
    }

    @DeleteMapping("/{idCotizacion}/adjuntos/{idAdjunto}")
    public ResponseEntity<Void> eliminarAdjunto(@PathVariable Long idCotizacion, @PathVariable Long idAdjunto) {
        cotizacionService.eliminarAdjunto(idAdjunto);
        return ResponseEntity.noContent().build();
    }
}