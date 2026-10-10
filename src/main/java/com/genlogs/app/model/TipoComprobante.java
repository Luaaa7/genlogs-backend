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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tipo_comprobante")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class TipoComprobante extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_comprobante")
    private Integer idTipoComprobante;
    
    @Column(name = "codigo_tipo", nullable = false, unique = true, length = 20)
    private String codigoTipo;
    
    @Column(name = "nombre_tipo", nullable = false, length = 60)
    private String nombreTipo;
    
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "serie_prefijo", columnDefinition = "CHAR(1)", nullable = false)
    private String seriePrefijo;
    
    @Column(name = "requiere_ruc", nullable = false)
    @Builder.Default
    private Boolean requiereRuc = false;
}
