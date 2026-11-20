package com.genlogs.app.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ContactoPublicoRequest;
import com.genlogs.app.service.ContactoPublicoService;
import com.genlogs.app.service.IpCliente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Formulario público de contacto. /api/public/** ya es permitAll. */
@RestController
@RequestMapping("/api/public/contacto")
@RequiredArgsConstructor
public class ContactoPublicoController {

    private final ContactoPublicoService contactoPublicoService;

    @PostMapping
    public ResponseEntity<Map<String, Boolean>> enviar(@Valid @RequestBody ContactoPublicoRequest request,
                                                       HttpServletRequest http) {
        boolean enviado = contactoPublicoService.recibir(request, IpCliente.de(http));
        return ResponseEntity.ok(Map.of("enviado", enviado));
    }
}
