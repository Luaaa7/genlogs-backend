package com.genlogs.app.vitrina;

import com.genlogs.app.vitrina.dto.CategoriaConteoResponse;
import com.genlogs.app.vitrina.dto.RepuestoPortalResponse;
import com.genlogs.app.vitrina.dto.RepuestoPublicoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fase 3 · Vitrina para clientes (RF-82). Apagada con genlogs.vitrina.habilitada=false (404).
 *
 * - Landing, sin sesión y SIN precio: /api/public/catalogo/repuestos y /categorias.
 *   (GET /api/public/catalogo ya existe en PublicController y lo sigue usando la landing
 *   actual; estas subrutas no chocan con él y ya están permitidas en SecurityConfig.)
 * - Portal del cliente, CON precio: /api/portal/repuestos (solo CLIENTE).
 * Ninguna respuesta lleva fuente, enlace ni precio original (D-13).
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "genlogs.vitrina.habilitada", havingValue = "true")
public class VitrinaPublicaController {

    private final VitrinaService vitrinaService;

    @GetMapping("/api/public/catalogo/repuestos")
    public ResponseEntity<List<RepuestoPublicoResponse>> catalogo(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer idCategoria,
            @RequestParam(required = false) String marca) {
        return ResponseEntity.ok(vitrinaService.catalogoPublico(q, idCategoria, marca));
    }

    @GetMapping("/api/public/catalogo/repuestos/{idProducto}")
    public ResponseEntity<RepuestoPublicoResponse> repuestoPublico(@PathVariable Long idProducto) {
        return ResponseEntity.ok(vitrinaService.repuestoPublico(idProducto));
    }

    @GetMapping("/api/public/catalogo/categorias")
    public ResponseEntity<List<CategoriaConteoResponse>> categorias() {
        return ResponseEntity.ok(vitrinaService.categoriasPublicas());
    }

    @GetMapping("/api/portal/repuestos")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<List<RepuestoPortalResponse>> repuestos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer idCategoria,
            @RequestParam(required = false) String marca) {
        return ResponseEntity.ok(vitrinaService.repuestosPortal(q, idCategoria, marca));
    }

    @GetMapping("/api/portal/repuestos/{idProducto}")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<RepuestoPortalResponse> repuesto(@PathVariable Long idProducto) {
        return ResponseEntity.ok(vitrinaService.repuestoPortal(idProducto));
    }
}
