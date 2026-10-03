package com.genlogs.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Coincide con ReporteRequest del frontend (src/types/reporte.types.ts):
 * tipoReporte y formato en MAYÚSCULAS, fechas en formato YYYY-MM-DD
 * (Spring las parsea solo con @DateTimeFormat por defecto ISO, que calza).
 */
@Getter @Setter
public class ReporteRequest {

    @NotNull
    private TipoReporte tipoReporte;

    @NotNull
    private LocalDate fechaInicio;

    @NotNull
    private LocalDate fechaFin;

    @NotNull
    private FormatoReporte formato;

    public enum TipoReporte { COTIZACIONES, ORDENES_COMPRA, FACTURACION, SERVICIOS, PRODUCTOS }
    public enum FormatoReporte { PDF, EXCEL }
}
