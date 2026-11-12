package com.genlogs.app.vitrina;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.vitrina.dto.ConvertirProductoRequest;
import com.genlogs.app.vitrina.dto.ImagenProductoRequest;
import com.genlogs.app.vitrina.dto.ProductoVitrinaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fase 3 · Vitrina interna (FE-D, "Convertir y publicar"). Apagada con
 * genlogs.vitrina.habilitada=false: el controlador no existe y las rutas dan 404.
 * Matriz v3.2 "Vitrina": Asistente Logístico crea; Gerente, Jefe Adm. y Asistente Adm. ven.
 */
@RestController
@RequestMapping("/api/vitrina/productos")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "genlogs.vitrina.habilitada", havingValue = "true")
public class VitrinaController {

    private final VitrinaService vitrinaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO','GERENTE_GENERAL','ASISTENTE_ADMINISTRATIVO')")
    public ResponseEntity<List<ProductoVitrinaResponse>> listar(@RequestParam(required = false) EstadoPublicacion estado) {
        return ResponseEntity.ok(vitrinaService.listarInternos(estado));
    }

    @GetMapping("/{idProducto}")
    @PreAuthorize("hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO','GERENTE_GENERAL','ASISTENTE_ADMINISTRATIVO')")
    public ResponseEntity<ProductoVitrinaResponse> detalle(@PathVariable Long idProducto) {
        return ResponseEntity.ok(vitrinaService.detalle(idProducto));
    }

    /** Convertir una referencia ÚTIL en producto BORRADOR (D-22: categoría y unidad obligatorias). */
    @PostMapping
    @PreAuthorize("hasRole('ASISTENTE_LOGISTICO')")
    public ResponseEntity<ProductoVitrinaResponse> convertir(@Valid @RequestBody ConvertirProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vitrinaService.convertir(request));
    }

    @PostMapping("/{idProducto}/imagenes")
    @PreAuthorize("hasRole('ASISTENTE_LOGISTICO')")
    public ResponseEntity<ProductoVitrinaResponse> agregarImagen(@PathVariable Long idProducto,
                                                                 @Valid @RequestBody ImagenProductoRequest request) {
        return ResponseEntity.ok(vitrinaService.agregarImagen(idProducto, request));
    }

    /** Con margen fuera de rango solo publica el Jefe Adm. (lo valida el servicio, D-26). */
    @PostMapping("/{idProducto}/publicar")
    @PreAuthorize("hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO')")
    public ResponseEntity<ProductoVitrinaResponse> publicar(@PathVariable Long idProducto) {
        return ResponseEntity.ok(vitrinaService.publicar(idProducto));
    }

    @PostMapping("/{idProducto}/ocultar")
    @PreAuthorize("hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO')")
    public ResponseEntity<ProductoVitrinaResponse> ocultar(@PathVariable Long idProducto) {
        return ResponseEntity.ok(vitrinaService.cambiarEstado(idProducto, EstadoPublicacion.OCULTO));
    }

    @PostMapping("/{idProducto}/retirar")
    @PreAuthorize("hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO')")
    public ResponseEntity<ProductoVitrinaResponse> retirar(@PathVariable Long idProducto) {
        return ResponseEntity.ok(vitrinaService.cambiarEstado(idProducto, EstadoPublicacion.RETIRADO));
    }
}
