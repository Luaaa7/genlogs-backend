package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pais")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Pais extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pais")
    private Integer idPais;

    @Column(name = "codigo_iso", nullable = false, unique = true, length = 3)
    private String codigoIso;

    @Column(name = "nombre_pais", nullable = false, unique = true, length = 100)
    private String nombrePais;
}
