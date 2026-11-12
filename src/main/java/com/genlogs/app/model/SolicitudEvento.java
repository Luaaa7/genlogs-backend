package com.genlogs.app.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bitácora de la solicitud (v3_2.sql): creación, cambios de estado, pausas,
 * reclasificaciones y reasignaciones. Solo se inserta; no lleva columnas de auditoría.
 */
@Entity
@Table(name = "solicitud_evento")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    @Column(name = "id_solicitud", nullable = false)
    private Long idSolicitud;

    @Column(name = "id_usuario")
    private Long idUsuario;

    /** Valor de TipoEventoSolicitud. */
    @Column(name = "tipo_evento", nullable = false, length = 20)
    private String tipoEvento;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;
}
