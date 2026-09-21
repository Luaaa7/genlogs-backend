package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Persona natural o jurídica identificada por su
 * documento (RUC u DNI). A partir de un mismo Tercero se puede abrir
 * un rol de Cliente, de Proveedor, o ambos (ver Cliente.java /
 * Proveedor.java), sin duplicar razón social, dirección ni contacto.
 */
@Entity
@Table(name = "tercero")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Tercero extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tercero")
    private Long idTercero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_documento", nullable = false)
    private TipoDocumento tipoDocumento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_distrito", nullable = false)
    private Distrito distrito;

    /** RUC (11 dígitos) o DNI (8 dígitos), validado en el service y por trigger en BD */
    @Column(name = "numero_documento", nullable = false, length = 11)
    private String numeroDocumento;

    /** Razón social (RUC) o nombres y apellidos (DNI) */
    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "correo", length = 150)
    private String correo;
}
