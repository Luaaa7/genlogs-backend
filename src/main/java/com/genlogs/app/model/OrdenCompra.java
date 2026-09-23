package com.genlogs.app.model;

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
