package com.genlogs.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.service.FactilizaService;

import lombok.RequiredArgsConstructor;

/**
 * Autocompletado de RUC/DNI (RF-08) para el formulario de registro de
 * cliente/proveedor. El frontend llama a este endpoint cuando el usuario
 * escribe el número de documento y pulsa "Buscar", y precarga razón social
 * y dirección con la respuesta.
 */
@RestController
@RequestMapping("/api/terceros")
@RequiredArgsConstructor
public class TerceroController {

    private final FactilizaService factilizaService;

    @GetMapping("/consultar-documento/{numero}")
    public ResponseEntity<ConsultaDocumentoResponse> consultarDocumento(@PathVariable String numero) {
        return ResponseEntity.ok(factilizaService.consultarPorNumero(numero));
    }
}
