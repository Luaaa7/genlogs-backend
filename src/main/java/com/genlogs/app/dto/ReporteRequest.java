package com.genlogs.app.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class ReporteRequest {

    @NotNull
    private TipoReporte tipoReporte;

    @NotNull
    private LocalDate fechaInicio;

    @NotNull
    private LocalDate fechaFin;

    @NotNull
    private FormatoReporte formato;

    public enum TipoReporte {
        COTIZACIONES, ORDENES_COMPRA, FACTURACION, SERVICIOS, PRODUCTOS
    }

    public enum FormatoReporte {
        PDF, EXCEL
    }

    public TipoReporte getTipoReporte() { return tipoReporte; }
    public void setTipoReporte(TipoReporte tipoReporte) { this.tipoReporte = tipoReporte; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public FormatoReporte getFormato() { return formato; }
    public void setFormato(FormatoReporte formato) { this.formato = formato; }
}