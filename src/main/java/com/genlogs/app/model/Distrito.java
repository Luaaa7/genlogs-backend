package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ubigeo", columnDefinition = "CHAR(6)", nullable = false, unique = true)
    private String ubigeo;

    @Column(name = "nombre_distrito", nullable = false, length = 100)
    private String nombreDistrito;
}
