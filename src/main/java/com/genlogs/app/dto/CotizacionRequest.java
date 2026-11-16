package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de POST /api/cotizaciones.
 *
 * El frontend (NuevaCotizacionPage.tsx -> cotizacion.schema.ts) manda los
 * nombres del formulario: "clienteId", "moneda" (código), "condicionPago"
 * (código) y "detalles". Para no romper ese contrato ni el canónico, cada
 * campo tiene su alias y la resolución/validación se hace en
 * CotizacionService.crear (los mensajes de error se mantienen idénticos).
 */
@Getter
@Setter
public class CotizacionRequest {

    /** Cliente de la cotización. Alias aceptado: {@code clienteId}. */
    private Long idCliente;
    private Long clienteId;

    /** Moneda por id. Alias aceptado: {@code moneda} con el código (PEN, USD, EUR). */
    private Integer idMoneda;
    private String moneda;

    /** Condición de pago por id. Alias aceptado: {@code condicionPago} con el código. */
    private Integer idCondicionPago;
    private String condicionPago;

    // DB: id_contacto es nullable ("cotización aún sin contacto asignado")
    private Long idContacto;

    private Integer idSectorEconomico;

    private String observaciones;

    /**
     * Propuesta C1, pendiente de confirmar con el equipo: tipo de cambio que el
     * formulario propone al preparar la cotización. El alta lo valida contra la
     * tabla; no se inventa ni se toma de una cotización que todavía no existe.
     */
    private BigDecimal tipoCambio;

    /** Propuesta C1: fecha del tipo de cambio propuesto. Va junto con {@code tipoCambio}. */
    private LocalDate fechaTipoCambio;

    /** Líneas de la cotización. Alias aceptado: {@code detalles}. */
    private List<@Valid CotizacionDetalleRequest> lineas;
    private List<@Valid CotizacionDetalleRequest> detalles;
}
