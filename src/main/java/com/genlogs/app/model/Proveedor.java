package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/** Rol PROVEEDOR de un Tercero (a lo más 1 ficha de proveedor por tercero). */
@Entity
@Table(name = "proveedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Proveedor extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Long idProveedor;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tercero", nullable = false, unique = true)
    private Tercero tercero;

    @Column(name = "situacion", nullable = false, length = 30)
    @Builder.Default
    private String situacion = "ACTIVO";
}
