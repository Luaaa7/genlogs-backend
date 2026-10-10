package com.genlogs.app.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.service.ReporteService;
import com.genlogs.app.service.ReporteService.ArchivoGenerado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Antes de este controller, POST /api/reportes/generar no existía: el
 * frontend (ReportesPage.tsx) llamaba a un endpoint que el backend nunca
 * implementó. Devuelve el archivo (Excel o PDF, según el formato pedido)
 * como bytes crudos, igual que reportesApi.ts espera (responseType: "blob").
 */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @PostMapping("/generar")
    public ResponseEntity<byte[]> generar(@Valid @RequestBody ReporteRequest request) {
        ArchivoGenerado archivo = reporteService.generar(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(archivo.nombreArchivo()).build());
        headers.setContentType(MediaType.parseMediaType(archivo.contentType()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(archivo.contenido());
    }
}
