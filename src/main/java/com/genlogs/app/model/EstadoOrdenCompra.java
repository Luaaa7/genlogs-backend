package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "estado_orden_compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class EstadoOrdenCompra extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_orden_compra")
    private Integer idEstadoOrdenCompra;
    
    @Column(name = "codigo_estado", nullable = false, unique = true, length = 20)
    private String codigoEstado;
    
    @Column(name = "nombre_estado", nullable = false, length = 60)
    private String nombreEstado;
    
    @Column(name = "es_final", nullable = false)
    @Builder.Default
    private Boolean esFinal = false;
}
