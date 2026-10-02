package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CategoriaProductoResponse;
import com.genlogs.app.dto.MarcaResponse;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.service.CatalogoProductoService;
import com.genlogs.app.service.UnidadMedidaService;

import lombok.RequiredArgsConstructor;

/**
 * Rutas cortas que usa el frontend para llenar los combos del catálogo de repuestos.
 * Reutilizan los mismos servicios que /api/catalogo-productos/* y /api/catalogos/unidades-medida.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoComboController {

    private final CatalogoProductoService catalogoProductoService;
    private final UnidadMedidaService unidadMedidaService;

    @GetMapping("/categorias-producto")
    public ResponseEntity<List<CategoriaProductoResponse>> categoriasProducto() {
        return ResponseEntity.ok(catalogoProductoService.listarCategorias());
    }

    @GetMapping("/marcas")
    public ResponseEntity<List<MarcaResponse>> marcas() {
        return ResponseEntity.ok(catalogoProductoService.listarMarcas());
    }

    @GetMapping("/unidades-medida")
    public ResponseEntity<List<UnidadMedida>> unidadesMedida() {
        return ResponseEntity.ok(unidadMedidaService.listarActivos());
    }
}
