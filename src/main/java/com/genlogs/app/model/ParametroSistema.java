package com.genlogs.app.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parametro configurable del sistema (RF-87), p. ej. MONTO_ALTO_VALOR_PEN = 20000.
 * Viene del Paso 0 (tabla en v3_2.sql). La lectura es de todos con
 * ParametrosService.get(clave); la edicion es de BE-D (solo JEFE_ADMINISTRATIVO).
 */
@Entity
@Table(name = "parametro_sistema")
@Getter
@Setter
@NoArgsConstructor
public class ParametroSistema {

    @Id
    @Column(name = "clave", length = 50)
    private String clave;

    @Column(name = "valor", nullable = false, length = 100)
    private String valor;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    /** id_usuario de quien lo cambio por ultima vez (NULL si viene de la migracion). */
    @Column(name = "editado_por")
    private Long editadoPor;

    @Column(name = "editado_en", nullable = false)
    private LocalDateTime editadoEn = LocalDateTime.now();
}