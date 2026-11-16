package com.genlogs.app.fuentes.prueba;

import java.time.LocalDateTime;

import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.model.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * M2 (v3.3, BPMN 9-C) · Un pedido de una corrida de la prueba de 10 pedidos en una tienda
 * (tabla fuente_prueba, v3_3.sql de Karla). Las columnas son exactamente las del script
 * (producción usa ddl-auto=validate).
 */
@Entity
@Table(name = "fuente_prueba",
        uniqueConstraints = @UniqueConstraint(name = "uq_fuente_prueba_pedido",
                columnNames = {"id_fuente", "corrida", "pedido"}))
@Getter
@Setter
@NoArgsConstructor
public class FuentePrueba extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prueba")
    private Long idPrueba;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    private FuenteBusquedaConfig fuente;

    /** 1, 2, 3...: cada vez que el Jefe guarda una prueba de 10 pedidos de esa tienda. */
    @Column(name = "corrida", nullable = false)
    private Integer corrida;

    /** Lo que se buscó, p. ej. "CAT 1R-0750". */
    @Column(name = "pedido", nullable = false, length = 120)
    private String pedido;

    @Column(name = "acerto", nullable = false)
    private Boolean acerto;

    /** Ficha que el Jefe tomó como acierto (o la más cercana); puede ir vacío. */
    @Column(name = "enlace", columnDefinition = "TEXT")
    private String enlace;

    @Column(name = "probado_por")
    private Long probadoPor;

    @Column(name = "probado_en", nullable = false)
    private LocalDateTime probadoEn = LocalDateTime.now();
}