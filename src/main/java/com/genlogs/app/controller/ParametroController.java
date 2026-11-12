package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ActualizarMargenesRepuestoRequest;
import com.genlogs.app.dto.ActualizarParametroRequest;
import com.genlogs.app.dto.ParametroResponse;
import com.genlogs.app.service.EdicionParametrosService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * parametro_sistema (RF-87). Cualquier rol interno los lista.
 * Solo el Jefe Administrativo cambia el valor; el filtro de URL no se toca.
 */
@RestController
@RequestMapping("/api/parametros")
@RequiredArgsConstructor
public class ParametroController {

    private final EdicionParametrosService edicionParametrosService;

    @GetMapping
    public ResponseEntity<List<ParametroResponse>> listar() {
        return ResponseEntity.ok(edicionParametrosService.listar());
    }

    @PutMapping("/{clave}")
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    public ResponseEntity<ParametroResponse> actualizar(@PathVariable String clave,
            @Valid @RequestBody ActualizarParametroRequest request) {
        return ResponseEntity.ok(edicionParametrosService.actualizar(clave, request.valor()));
    }

    /**
     * Los tres márgenes de repuestos (D-17) juntos: se guardan los tres o ninguno.
     * Spring prioriza esta ruta literal sobre PUT /{clave}.
     */
    @PutMapping("/margenes-repuesto")
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    public ResponseEntity<List<ParametroResponse>> actualizarMargenesRepuesto(
            @Valid @RequestBody ActualizarMargenesRepuestoRequest request) {
        return ResponseEntity.ok(edicionParametrosService.actualizarMargenesRepuesto(
                request.min(), request.porDefecto(), request.max()));
    }
}
