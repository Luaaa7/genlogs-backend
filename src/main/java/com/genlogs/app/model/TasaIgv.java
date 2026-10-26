package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Historial de la tasa del IGV. Rige la tasa con la fecha "vigente_desde" más
 * reciente que no sea futura. Cada cotización y factura guarda el porcentaje
 * con que se creó, así un cambio de tasa no altera los documentos anteriores.
 */
@Entity
@Table(name = "tasa_igv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TasaIgv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tasa_igv")
    private Integer idTasaIgv;

    /** Porcentaje, por ejemplo 18.00. */
    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "vigente_desde", nullable = false, unique = true)
    private LocalDate vigenteDesde;

    @Column(name = "user_create", length = 50)
    private String userCreate;

    @Column(name = "date_create")
    private LocalDateTime dateCreate;
}