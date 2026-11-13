package com.genlogs.app.fuentes.config;

import com.genlogs.app.model.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regla de extracción de una tienda para una página y un campo (tabla fuente_selector,
 * v3_3.sql). La edita el Jefe Administrativo (M4) y la usan los lectores (A4).
 */
@Entity
@Table(name = "fuente_selector",
        uniqueConstraints = @UniqueConstraint(name = "uq_fuente_selector_campo",
                columnNames = {"id_fuente", "pagina", "campo"}))
@Getter
@Setter
@NoArgsConstructor
public class FuenteSelector extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_selector")
    private Long idSelector;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    private FuenteBusquedaConfig fuente;

    @Enumerated(EnumType.STRING)
    @Column(name = "pagina", nullable = false, length = 10)
    private PaginaSelector pagina;

    @Enumerated(EnumType.STRING)
    @Column(name = "campo", nullable = false, length = 15)
    private CampoSelector campo;

    @Column(name = "regla", nullable = false, columnDefinition = "TEXT")
    private String regla;

    @Column(name = "editado_por")
    private Long editadoPor;
}
