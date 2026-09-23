package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Minerales que extrae cada unidad minera. PK compuesta, sin id propio. */
@Entity
@Table(name = "empresa_minera_mineral")
@IdClass(EmpresaMineraMineralId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class EmpresaMineraMineral extends Auditable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_empresa_minera")
    private EmpresaMinera empresaMinera;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_mineral")
    private Mineral mineral;

    /** Si es el mineral principal de esa operación. DB: DEFAULT FALSE. */
    @Column(name = "es_principal", nullable = false)
    private Boolean esPrincipal;
}
