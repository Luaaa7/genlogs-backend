package com.genlogs.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pago en línea ya registrado como cobro (BPMN 4.1, v3_4_pago_en_linea.sql).
 * La llave única (proveedor, id_pago_externo) evita registrar dos veces el mismo pago.
 */
@Entity
@Table(name = "pago_en_linea",
        uniqueConstraints = @UniqueConstraint(name = "uq_pago_en_linea_externo",
                columnNames = {"proveedor", "id_pago_externo"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class PagoEnLinea extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago_en_linea")
    private Long idPagoEnLinea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_facturacion", nullable = false)
    private Facturacion facturacion;

    /** MERCADO_PAGO. */
    @Column(name = "proveedor", nullable = false, length = 20)
    private String proveedor;

    /** Id del pago en la pasarela (Mercado Pago: payment id). */
    @Column(name = "id_pago_externo", nullable = false, length = 40)
    private String idPagoExterno;

    /** Estado con el que se registró (approved). */
    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "monto", nullable = false, precision = 16, scale = 2)
    private BigDecimal monto;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "moneda", nullable = false, columnDefinition = "CHAR(3)", length = 3)
    private String moneda;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;
}