package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.DecisionPortalRequest;
import com.genlogs.app.dto.PortalCotizacionResponse;
import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.service.CotizacionService;
import com.genlogs.app.service.PermisosCotizacion;
import com.genlogs.app.service.PortalCotizacionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/portal/cotizaciones")
@PreAuthorize(PermisosCotizacion.PORTAL)
@RequiredArgsConstructor
public class PortalCotizacionController {

    private final PortalCotizacionService portalCotizacionService;
    private final CotizacionService cotizacionService;

    @GetMapping
    public ResponseEntity<List<PortalCotizacionResponse>> listar() {
        return ResponseEntity.ok(portalCotizacionService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortalCotizacionResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(portalCotizacionService.detalle(id));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<PortalCotizacionResponse> aprobar(
            @PathVariable Long id, @RequestBody(required = false) DecisionPortalRequest request) {
        PortalCotizacionResponse respuesta = portalCotizacionService.aprobar(id, request);
        // La aprobación ya se confirmó. La orden va en otra transacción.
        cotizacionService.registrarOrdenSiAprobada(id, CodigoEstadoCotizacion.APROBADA.name());
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<PortalCotizacionResponse> rechazar(
            @PathVariable Long id, @RequestBody(required = false) DecisionPortalRequest request) {
        return ResponseEntity.ok(portalCotizacionService.rechazar(id, request));
    }
}
