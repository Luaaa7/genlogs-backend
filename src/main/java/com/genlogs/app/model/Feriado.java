package com.genlogs.app.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Feriado que no cuenta como día hábil para los plazos de respuesta (D-18).
 * Se precargan los nacionales 2026-2027; el Jefe Administrativo agrega o da de baja
 * fechas futuras. Baja lógica con status = 'I' (D-29).
 */
@Entity
@Table(name = "feriado")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Feriado extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feriado")
    private Integer idFeriado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "descripcion", nullable = false, length = 100)
    private String descripcion;
}
