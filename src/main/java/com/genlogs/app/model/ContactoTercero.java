package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contacto_tercero")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ContactoTercero extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contacto")
    private Long idContacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tercero", nullable = false)
    private Tercero tercero;

    @Column(name = "nombres", nullable = false, length = 150)
    private String nombres;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "es_principal", nullable = false)
    @Builder.Default
    private Boolean esPrincipal = false;
}