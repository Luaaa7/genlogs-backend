package com.genlogs.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.AvisosResponse;
import com.genlogs.app.service.NotificacionService;

import lombok.RequiredArgsConstructor;

/**
 * Campanita de avisos internos (D-21, D-25). Solo personal interno (regla en
 * SecurityConfig); cada usuario ve y marca solo los suyos. Los avisos no se borran.
 *
 * GET   /api/notificaciones             -> 200 AvisosResponse
 * PATCH /api/notificaciones/{id}/leida  -> 204; 404 si no existe o no es suyo
 * Sin sesion: 401. Rol CLIENTE: 403.
 */
@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public ResponseEntity<AvisosResponse> misAvisos(Authentication autenticacion) {
        return ResponseEntity.ok(notificacionService.misAvisos(autenticacion.getName()));
    }

    @PatchMapping("/{idNotificacion}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long idNotificacion, Authentication autenticacion) {
        notificacionService.marcarLeida(idNotificacion, autenticacion.getName());
        return ResponseEntity.noContent().build();
    }
}