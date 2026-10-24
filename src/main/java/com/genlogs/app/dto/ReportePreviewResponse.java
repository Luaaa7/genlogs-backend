package com.genlogs.app.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Vista previa de un reporte: los MISMOS datos que se descargarían, listos
 * para pintarse como tabla.
 *
 * <p>La vista previa y el archivo se arman con la misma lógica de negocio
 * (mismo tipo de reporte, mismo periodo y mismos filtros): el servicio usa
 * exactamente el mismo armado de filas para las dos cosas.
 *
 * <p>El formato NO cambia los datos: solo decide si la descarga es .xlsx o
 * .pdf. Por eso la vista previa devuelve el formato pedido, pero las filas
 * siempre son las mismas.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReportePreviewResponse {

    private ReporteRequest.TipoReporte tipoReporte;
    private ReporteRequest.FormatoReporte formato;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    /** Títulos de las columnas (mismos que usa el Excel/PDF). */
    private List<String> encabezados;
    /** Filas del reporte, en el mismo orden que en el archivo descargable. */
    private List<List<String>> filas;

    /** Cantidad REAL de filas que tiene el reporte en el periodo pedido. */
    private int totalFilas;
    /** Cuántas filas se devuelven en esta vista previa. */
    private int filasMostradas;
    /** true si totalFilas > filasMostradas (la vista previa está recortada). */
    private boolean truncado;
}
