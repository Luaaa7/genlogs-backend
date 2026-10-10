package com.genlogs.app.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CotizacionEstadoItemResponse;
import com.genlogs.app.dto.DashboardIndicadoresResponse;
import com.genlogs.app.dto.FacturacionHistoricoItemResponse;
import com.genlogs.app.service.DashboardService;

import lombok.RequiredArgsConstructor;

/**
 * Alimenta el Dashboard del frontend (src/api/dashboardApi.ts):
 *  - GET /api/dashboard/indicadores
 *  - GET /api/dashboard/graficos/facturacion?periodo=mensual|trimestral|anual
 *  - GET /api/dashboard/graficos/cotizaciones-estado
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/indicadores")
    public ResponseEntity<DashboardIndicadoresResponse> indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(dashboardService.obtenerIndicadores(desde, hasta));
    }

    @GetMapping("/graficos/facturacion")
    public ResponseEntity<List<FacturacionHistoricoItemResponse>> graficoFacturacion(
            @RequestParam(required = false, defaultValue = "mensual") String periodo) {
        return ResponseEntity.ok(dashboardService.obtenerHistoricoFacturacion(periodo));
    }

    @GetMapping("/graficos/cotizaciones-estado")
    public ResponseEntity<List<CotizacionEstadoItemResponse>> graficoCotizacionesPorEstado() {
        return ResponseEntity.ok(dashboardService.obtenerCotizacionesPorEstado());
    }
}
