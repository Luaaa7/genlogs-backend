package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.CambiarEstadoOrdenCompraRequest;
import com.genlogs.app.dto.OrdenCompraRequest;
import com.genlogs.app.dto.OrdenCompraResponse;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.service.OrdenCompraService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ordenes-compra")
@RequiredArgsConstructor
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;

    @GetMapping
    public ResponseEntity<PaginaResponse<OrdenCompraResponse>> listar(
            @RequestParam(required = false) String estadoCodigo,
            @RequestParam(required = false) Long idCotizacion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(ordenCompraService.listar(estadoCodigo, idCotizacion, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenCompraResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordenCompraService.buscarPorId(id));
    }

    @GetMapping("/numero/{numero}")
    public ResponseEntity<OrdenCompraResponse> buscarPorNumero(@PathVariable String numero) {
        return ResponseEntity.ok(ordenCompraService.buscarPorNumero(numero));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<OrdenCompraResponse>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(ordenCompraService.listarPorCliente(idCliente));
    }

    @PostMapping
    public ResponseEntity<OrdenCompraResponse> crear(@Valid @RequestBody OrdenCompraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenCompraService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<OrdenCompraResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody CambiarEstadoOrdenCompraRequest request) {
        return ResponseEntity.ok(ordenCompraService.cambiarEstado(id, request));
    }

    @PostMapping(value = "/{id}/archivo", consumes = "multipart/form-data")
    public ResponseEntity<OrdenCompraResponse> agregarArchivo(
            @PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(ordenCompraService.agregarArchivo(id, archivo));
    }
}
