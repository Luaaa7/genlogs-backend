package com.genlogs.app.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ReportePreviewResponse;
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

    /**
     * Vista previa del reporte: los mismos datos que se descargarían, en
     * JSON, para pintarlos como tabla.
     *
     * <p>El cuerpo es el mismo que el de la descarga (tipoReporte,
     * fechaInicio, fechaFin, formato). A diferencia de /generar, el formato
     * es opcional aquí: la vista previa siempre es una tabla, así que no
     * obliga a mandarlo.
     */
    @PostMapping("/preview")
    public ResponseEntity<ReportePreviewResponse> preview(@RequestBody ReporteRequest request) {
        return ResponseEntity.ok(reporteService.preview(request));
    }

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
