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

    /** Distrito del catálogo INEI. Obligatorio solo si el país es Perú (D-15, RF-81); v3_2.sql quita el NOT NULL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_distrito")
    private Distrito distrito;

    /** País del tercero (v3_2.sql). NULL en datos viejos equivale a Perú. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pais")
    private Pais pais;

    /** Solo extranjeros (v3_2_ubigeo.sql): ubicación en texto libre, sin catálogo. */
    @Column(name = "region_extranjero", length = 100)
    private String regionExtranjero;

    @Column(name = "ciudad_extranjero", length = 100)
    private String ciudadExtranjero;

    @Column(name = "codigo_postal_extranjero", length = 20)
    private String codigoPostalExtranjero;

    /** RUC (11 dígitos), DNI (8 dígitos) o pasaporte (6 a 12 letras y números); validado en el service y por trigger en BD */
    @Column(name = "numero_documento", nullable = false, length = 11)
    private String numeroDocumento;

    /** Razón social (RUC) o nombre completo (DNI y pasaporte), armado con los tres campos de abajo. */
    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    /** DNI y pasaporte: nombres de la persona (null para RUC). */
    @Column(name = "nombres", length = 120)
    private String nombres;

    @Column(name = "apellido_paterno", length = 60)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 60)
    private String apellidoMaterno;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "correo", length = 150)
    private String correo;
}