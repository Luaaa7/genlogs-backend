package com.genlogs.app.sourcing;

import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;
import com.genlogs.app.sourcing.dto.CambiarEstadoReferenciaRequest;
import com.genlogs.app.sourcing.dto.ReferenciaResponse;
import com.genlogs.app.sourcing.dto.ResultadoBusquedaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fase 2 · Búsqueda. Apagado con genlogs.busqueda.habilitada=false (Paso 0):
 * mientras esté apagado el controlador no se registra y las rutas dan 404.
 *
 * Permisos (matriz v3.2, "Búsqueda e historial"): Asistente Logístico crea;
 * Gerente General y Jefe Administrativo solo ven.
 */
@RestController
@RequestMapping("/api/sourcing")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "genlogs.busqueda.habilitada", havingValue = "true")
public class SourcingController {

    private final SourcingService sourcingService;

    @PostMapping("/buscar")
    @PreAuthorize("hasRole('ASISTENTE_LOGISTICO')")
    public ResponseEntity<ResultadoBusquedaResponse> buscar(@Valid @RequestBody BuscarReferenciasRequest request) {
        return ResponseEntity.ok(sourcingService.buscar(request));
    }

    @GetMapping("/historial")
    @PreAuthorize("hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO','GERENTE_GENERAL')")
    public ResponseEntity<List<ReferenciaResponse>> historial(
            @RequestParam(required = false) Long idSolicitudItem,
            @RequestParam(required = false) String numeroParte) {
        return ResponseEntity.ok(sourcingService.listar(idSolicitudItem, numeroParte));
    }

    @PatchMapping("/{idSourcing}/estado")
    @PreAuthorize("hasRole('ASISTENTE_LOGISTICO')")
    public ResponseEntity<ReferenciaResponse> cambiarEstado(
            @PathVariable Long idSourcing,
            @Valid @RequestBody CambiarEstadoReferenciaRequest request) {
        return ResponseEntity.ok(sourcingService.cambiarEstado(idSourcing, request.estado()));
    }
}
