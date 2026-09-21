package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "distrito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Distrito extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_distrito")
    private Integer idDistrito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_provincia", nullable = false)
    private Provincia provincia;

    @Column(name = "ubigeo", nullable = false, unique = true, length = 6)
    private String ubigeo;

    @Column(name = "nombre_distrito", nullable = false, length = 100)
    private String nombreDistrito;
}
