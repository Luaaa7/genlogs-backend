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
 * Resultado guardado de una consulta RUC/DNI a Factiliza, para no gastar
 * otro token cuando se vuelve a consultar el mismo número.
 * Tabla creada con sql/consulta_documento_cache.sql.
 */
@Entity
@Table(name = "consulta_documento_cache")
@Getter
@Setter
@NoArgsConstructor
public class ConsultaDocumentoCache extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consulta_documento_cache")
    private Long idConsultaDocumentoCache;

    @Column(name = "numero_documento", length = 11, nullable = false, unique = true)
    private String numeroDocumento;

    /** "RUC" o "DNI". */
    @Column(name = "tipo_documento", length = 3, nullable = false)
    private String tipoDocumento;

    @Column(name = "razon_social", length = 500)
    private String razonSocial;

    @Column(name = "direccion", length = 500)
    private String direccion;

    /** Cuándo se consultó por última vez en Factiliza (define el vencimiento). */
    @Column(name = "fecha_consulta", nullable = false)
    private LocalDateTime fechaConsulta;
}
