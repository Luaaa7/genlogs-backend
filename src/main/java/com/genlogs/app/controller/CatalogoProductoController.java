package com.genlogs.app.controller;

import com.genlogs.app.dto.CaracteristicaTecnicaResponse;
import com.genlogs.app.dto.CategoriaProductoResponse;
import com.genlogs.app.dto.MarcaResponse;
import com.genlogs.app.service.CatalogoProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de solo lectura para poblar los combos del formulario de
 * producto en el frontend (categorías, marcas, características).
 */
@RestController
@RequestMapping("/api/catalogo-productos")
@RequiredArgsConstructor
public class CatalogoProductoController {

    private final CatalogoProductoService catalogoProductoService;

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaProductoResponse>> listarCategorias() {
        return ResponseEntity.ok(catalogoProductoService.listarCategorias());
    }

    @GetMapping("/marcas")
    public ResponseEntity<List<MarcaResponse>> listarMarcas() {
        return ResponseEntity.ok(catalogoProductoService.listarMarcas());
    }

    @GetMapping("/caracteristicas")
    public ResponseEntity<List<CaracteristicaTecnicaResponse>> listarCaracteristicas() {
        return ResponseEntity.ok(catalogoProductoService.listarCaracteristicas());
    }
}
