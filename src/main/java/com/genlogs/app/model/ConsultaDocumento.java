package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RUC o DNI ya consultado en Factiliza. Se guarda la respuesta para que la
 * siguiente consulta del mismo número salga de la base de datos y no gaste
 * otra consulta pagada.
 */
@Entity
@Table(name = "consulta_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consulta")
    private Long idConsulta;

    @Column(name = "numero_documento", nullable = false, unique = true, length = 11)
    private String numeroDocumento;

    /** "RUC" o "DNI". */
    @Column(name = "tipo_documento", nullable = false, length = 3)
    private String tipoDocumento;

    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @Column(name = "nombres", length = 120)
    private String nombres;

    @Column(name = "apellido_paterno", length = 60)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 60)
    private String apellidoMaterno;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "ubigeo", length = 6)
    private String ubigeo;

    /** Distrito del catálogo que corresponde al UBIGEO (puede ser null). */
    @Column(name = "id_distrito")
    private Integer idDistrito;

    @Column(name = "fecha_consulta", nullable = false)
    private LocalDateTime fechaConsulta;

    /** Cuántas veces se pidió este número (la primera a Factiliza, las demás desde aquí). */
    @Column(name = "veces_consultado", nullable = false)
    private Integer vecesConsultado;
}