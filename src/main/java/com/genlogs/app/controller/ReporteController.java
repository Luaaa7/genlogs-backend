package com.genlogs.app.controller;

import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @PostMapping("/generar")
    public ResponseEntity<byte[]> generar(@Valid @RequestBody ReporteRequest request) throws IOException {
        byte[] archivo = reporteService.generarReporte(request);

        String extension = request.getFormato() == ReporteRequest.FormatoReporte.EXCEL ? "xlsx" : "pdf";
        MediaType tipoContenido = request.getFormato() == ReporteRequest.FormatoReporte.EXCEL
            ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            : MediaType.APPLICATION_PDF;

        String nombreArchivo = "reporte-" + request.getTipoReporte().name().toLowerCase() + "." + extension;

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
            .contentType(tipoContenido)
            .body(archivo);
    }
}