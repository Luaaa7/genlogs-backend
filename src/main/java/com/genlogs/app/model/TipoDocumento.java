package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * [NUEVO V8] Catálogo de documentos de identidad: RUC, DNI, CE, PAS.
 * Permite que un Tercero se identifique con RUC (persona jurídica)
 * o DNI (persona natural), entre otros.
 */
@Entity
@Table(name = "tipo_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class TipoDocumento extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_documento")
    private Integer idTipoDocumento;

    /** RUC, DNI, CE, PAS */
    @Column(name = "codigo_tipo", nullable = false, unique = true, length = 10)
    private String codigoTipo;

    @Column(name = "nombre_tipo", nullable = false, length = 60)
    private String nombreTipo;

    /** Longitud exacta esperada: 11 para RUC, 8 para DNI, etc. */
    @Column(name = "longitud_documento", nullable = false)
    private Short longitudDocumento;

    @Column(name = "solo_numerico", nullable = false)
    @Builder.Default
    private Boolean soloNumerico = true;
}