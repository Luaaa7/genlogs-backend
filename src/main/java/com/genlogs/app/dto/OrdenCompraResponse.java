package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrdenCompraResponse {
    private Long idOrdenCompra;
    private Long idCotizacion;
    private String codigoCotizacion;
    private String numeroOrdenCompra;
    private String clienteRazonSocial;
    private String cliente;
    private String estadoCodigo;
    private LocalDate fechaEmisionCliente;
    private LocalDate fechaRecepcion;
    private String estado;
    private String urlArchivo;
    private String observaciones;
    // Tomados de la cotización origen: vw_cotizacion_totales no cubre
    // órdenes de compra, así que se calculan vía CotizacionRepository.
    private BigDecimal total;
    private String moneda;

    /** Al pasar a ATENDIDA: código del comprobante que se emitió solo (F001-00000008). */
    @Setter
    private String comprobanteEmitido;
    /** Al pasar a ATENDIDA: por qué no se pudo emitir solo (para hacerlo a mano). */
    @Setter
    private String avisoComprobante;
}