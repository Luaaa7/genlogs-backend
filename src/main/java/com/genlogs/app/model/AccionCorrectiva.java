package com.genlogs.app.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.genlogs.app.enums.EstadoAccionCorrectiva;

/** Acción correctiva de un periodo. La registra el Jefe; no se crea sola. */
@Entity
@Table(name = "accion_correctiva")
@Getter
@Setter
@NoArgsConstructor
public class AccionCorrectiva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_accion")
    private Long idAccion;

    @Column(name = "anio", nullable = false)
    private Short anio;

    @Column(name = "mes", nullable = false)
    private Short mes;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoAccionCorrectiva estado = EstadoAccionCorrectiva.PENDIENTE;

    @Column(name = "creada_por", nullable = false)
    private Long creadaPor;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
