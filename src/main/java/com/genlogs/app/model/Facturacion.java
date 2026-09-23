package com.genlogs.app.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "facturacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class Facturacion extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_facturacion")
    private Long idFacturacion;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_comprobante", nullable = false)
    private TipoComprobante tipoComprobante;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_facturacion", nullable = false)
    private EstadoFacturacion estadoFacturacion;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_condicion_pago", nullable = false)
    private CondicionPago condicionPago;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_moneda", nullable = false)
    private Moneda moneda;
    
    @Column(name = "serie_comprobante", nullable = false, length = 4)
    private String serieComprobante;
    
    @Column(name = "numero_comprobante", nullable = false, length = 8)
    private String numeroComprobante;
    
    @Column(name = "fecha_emision", nullable = false)
    @Builder.Default
    private LocalDate fechaEmision = LocalDate.now();
    
    @Column(name = "fecha_vencimiento", nullable = false)
    @Builder.Default
    private LocalDate fechaVencimiento = LocalDate.now().plusDays(30);
    
    @Column(name = "monto_pagado", nullable = false, precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal montoPagado = BigDecimal.ZERO;
    
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}
