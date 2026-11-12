package com.genlogs.app.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "orden_compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class OrdenCompra extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_compra")
    private Long idOrdenCompra;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cotizacion", nullable = false)
    private Cotizacion cotizacion;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_orden_compra", nullable = false)
    private EstadoOrdenCompra estadoOrdenCompra;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_condicion_pago", nullable = false)
    private CondicionPago condicionPago;
    
    @Column(name = "numero_orden_compra", nullable = false, length = 20)
    private String numeroOrdenCompra;

    /** N.º de la OC de la minera. v3_2.sql: VARCHAR(40), nulo. */
    @Column(name = "numero_oc_cliente", length = 40)
    private String numeroOcCliente;

    /** Monto leído o confirmado. v3_2.sql: NUMERIC(14,2), nulo. */
    @Column(name = "monto_oc_cliente", precision = 14, scale = 2)
    private BigDecimal montoOcCliente;

    /** Código leído o confirmado. v3_2_be_c.sql: CHAR(3), nulo. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "moneda_oc_cliente", columnDefinition = "CHAR(3)", length = 3)
    private String monedaOcCliente;

    /** True si el PDF dejó al menos un dato. v3_2.sql: BOOLEAN NOT NULL DEFAULT FALSE. */
    @Column(name = "oc_leida_auto", nullable = false)
    @Builder.Default
    private boolean ocLeidaAuto = false;

    /** Usuario que confirmó o corrigió lo leído. v3_2.sql: BIGINT nulo, FK a usuario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oc_confirmada_por")
    private Usuario ocConfirmadaPor;
    
    @Column(name = "fecha_emision_cliente")
    private LocalDate fechaEmisionCliente;
    
    @Column(name = "fecha_recepcion", nullable = false)
    @Builder.Default
    private LocalDate fechaRecepcion = LocalDate.now();
    
    @Column(name = "url_archivo", columnDefinition = "TEXT")
    private String urlArchivo;
    
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}
