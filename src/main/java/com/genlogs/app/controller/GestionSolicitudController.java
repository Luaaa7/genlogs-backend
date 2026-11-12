package com.genlogs.app.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.gestion.EventoSolicitudResponse;
import com.genlogs.app.dto.gestion.PausarSolicitudRequest;
import com.genlogs.app.dto.gestion.ReasignarSolicitudRequest;
import com.genlogs.app.dto.gestion.ReclasificarSolicitudRequest;
import com.genlogs.app.dto.gestion.SolicitudInternaResponse;
import com.genlogs.app.dto.gestion.SolicitudVencimientoResponse;
import com.genlogs.app.service.GestionSolicitudService;
import com.genlogs.app.service.VencimientoSolicitudService;

import jakarta.validation.Valid;

/**
 * Gestión interna de solicitudes (BE-B). Solo personal interno; el cliente recibe 403.
 *
 * PATCH /api/solicitudes/{id}/iniciar       PENDIENTE → EN_REVISION
 * PATCH /api/solicitudes/{id}/pausar        {motivo, fechaEstimada?} → EN_ESPERA_TERCERO
 * PATCH /api/solicitudes/{id}/reanudar      → EN_REVISION, plazo corrido lo pausado
 * PATCH /api/solicitudes/{id}/reclasificar  {tipoPlazo}; la 2.ª vez solo el Jefe Adm.
 * PATCH /api/solicitudes/{id}/reasignar     {idUsuario}; solo el Jefe Adm.
 * GET   /api/solicitudes/{id}/historial     cada cambio, en orden
 * GET   /api/solicitudes/vencimiento        abiertas por vencimiento con semáforo (?soloMias=true)
 * 409 si la solicitud ya está cotizada, rechazada o cancelada, o no está en el estado que pide la acción.
 */
@RestController
@RequestMapping("/api/solicitudes")
public class GestionSolicitudController {

    private static final String GESTIONAN = "hasAnyRole('ASISTENTE_LOGISTICO','JEFE_ADMINISTRATIVO')";

    private final GestionSolicitudService gestion;
    private final VencimientoSolicitudService vencimientoService;

    public GestionSolicitudController(GestionSolicitudService gestion, VencimientoSolicitudService vencimientoService) {
        this.gestion = gestion;
        this.vencimientoService = vencimientoService;
    }

    @PatchMapping("/{id}/iniciar")
    @PreAuthorize(GESTIONAN)
    public ResponseEntity<SolicitudInternaResponse> iniciar(@PathVariable Long id) {
        return ResponseEntity.ok(gestion.iniciar(id));
    }

    @PatchMapping("/{id}/pausar")
    @PreAuthorize(GESTIONAN)
    public ResponseEntity<SolicitudInternaResponse> pausar(@PathVariable Long id,
            @Valid @RequestBody PausarSolicitudRequest request) {
        return ResponseEntity.ok(gestion.pausar(id, request.motivo(), request.fechaEstimada()));
    }

    @PatchMapping("/{id}/reanudar")
    @PreAuthorize(GESTIONAN)
    public ResponseEntity<SolicitudInternaResponse> reanudar(@PathVariable Long id) {
        return ResponseEntity.ok(gestion.reanudar(id));
    }

    @PatchMapping("/{id}/reclasificar")
    @PreAuthorize(GESTIONAN)
    public ResponseEntity<SolicitudInternaResponse> reclasificar(@PathVariable Long id,
            @Valid @RequestBody ReclasificarSolicitudRequest request) {
        return ResponseEntity.ok(gestion.reclasificar(id, request.tipoPlazo()));
    }

    @PatchMapping("/{id}/reasignar")
    @PreAuthorize("hasRole('JEFE_ADMINISTRATIVO')")
    public ResponseEntity<SolicitudInternaResponse> reasignar(@PathVariable Long id,
            @Valid @RequestBody ReasignarSolicitudRequest request) {
        return ResponseEntity.ok(gestion.reasignar(id, request.idUsuario()));
    }

    /** Lista interna por vencimiento con semáforo; ?soloMias=true filtra las asignadas a quien consulta. */
    @GetMapping("/vencimiento")
    public ResponseEntity<List<SolicitudVencimientoResponse>> vencimiento(
            @RequestParam(defaultValue = "false") boolean soloMias, Authentication auth) {
        return ResponseEntity.ok(vencimientoService.lista(LocalDateTime.now(), soloMias ? auth.getName() : null));
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<List<EventoSolicitudResponse>> historial(@PathVariable Long id) {
        return ResponseEntity.ok(gestion.historial(id));
    }
}
