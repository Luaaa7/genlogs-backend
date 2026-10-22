package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Respuesta de una cotización.
 *
 * Mantiene los campos originales (clienteRazonSocial, estado, moneda) y suma
 * los alias que consume el frontend (src/types/cotizacion.types.ts): id,
 * codigo, estadoCodigo, monedaCodigo, clienteNombre, detalles, seguimientos y
 * adjuntos. Todos son additive, así que no rompen a ningún consumidor previo.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CotizacionResponse {

    private Long idCotizacion;
    /** Alias de idCotizacion. */
    private Long id;
    private String codigoCotizacion;
    /** Alias de codigoCotizacion. */
    private String codigo;

    private Long idCliente;
    private Long idContacto;
    private Long idUsuarioVendedor;
    private Integer idEstadoCotizacion;
    private Integer idMoneda;
    private Integer idCondicionPago;
    private Integer idSectorEconomico;

    private String clienteRazonSocial;
    /** Alias de clienteRazonSocial. */
    private String clienteNombre;
    /** Alias de clienteRazonSocial. */
    private String cliente;
    /** Correo del contacto (o del tercero): el frontend lo usa para enviar la cotización. */
    private String clienteEmail;

    private LocalDate fechaCotizacion;
    private LocalDate fechaValidez;
    /** Fecha de alta (columna date_create). */
    private LocalDateTime fechaCreacion;

    /** Nombre legible del estado (p. ej. "Borrador"). */
    private String estado;
    /** Código del estado (p. ej. "BORRADOR"). */
    private String estadoCodigo;
    /** Alias de estadoCodigo. */
    private String estadoCotizacion;

    /** Código de la condición de pago (p. ej. "CREDITO_30"). */
    private String condicionPago;

    // Calculados desde vw_cotizacion_totales (la tabla no guarda el total)
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;

    /**
     * Código de la moneda (p. ej. "PEN"). El frontend lo tipa como Moneda y lo
     * pasa a Intl.NumberFormat({currency}), que lanza RangeError con un
     * símbolo ("S/"), así que aquí va el CÓDIGO y no el símbolo.
     */
    private String moneda;
    /** Alias de moneda. */
    private String monedaCodigo;
    /** Símbolo de la moneda (p. ej. "S/"). */
    private String monedaSimbolo;
    private String monedaNombre;

    private String observaciones;

    /** Líneas, seguimientos y adjuntos: se llenan en el detalle (GET /{id}). */
    private List<CotizacionDetalleResponse> detalles;
    private List<SeguimientoCotizacionResponse> seguimientos;
    private List<AdjuntoCotizacionResponse> adjuntos;
}
