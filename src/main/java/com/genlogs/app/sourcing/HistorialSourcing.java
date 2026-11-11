package com.genlogs.app.sourcing;

import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Referencia encontrada en una fuente (tabla historial_sourcing, v3_2.sql).
 * Solo la ve el personal interno. No extiende Auditable porque la tabla no
 * tiene las columnas de auditoría (user_create, status, ...): con
 * ddl-auto=validate, las columnas de aquí deben ser EXACTAMENTE las de la tabla.
 *
 * La baja de una referencia es el estado DESCARTADO; nunca se borra (D-33).
 */
@Entity
@Table(name = "historial_sourcing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialSourcing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sourcing")
    private Long idSourcing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud_item")
    private SolicitudItem solicitudItem;

    /** EBAY, MERCADO_LIBRE, AMAZON, ALIBABA o PROVEEDOR. */
    @Column(name = "fuente", nullable = false, length = 15)
    private String fuente;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", nullable = false, length = 10)
    private MetodoBusqueda metodo;

    @Column(name = "consulta", columnDefinition = "TEXT")
    private String consulta;

    @Column(name = "url_referencia", columnDefinition = "TEXT")
    private String urlReferencia;

    @Column(name = "url_imagen_ref", columnDefinition = "TEXT")
    private String urlImagenRef;

    @Column(name = "titulo", columnDefinition = "TEXT")
    private String titulo;

    @Column(name = "marca_texto", length = 100)
    private String marcaTexto;

    @Column(name = "numero_parte", length = 60)
    private String numeroParte;

    @Column(name = "precio_ref", precision = 12, scale = 2)
    private BigDecimal precioRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_moneda")
    private Moneda moneda;

    @Column(name = "tipo_cambio_usado", precision = 10, scale = 4)
    private BigDecimal tipoCambioUsado;

    /** NACIONAL o IMPORTADO; lo completa la captura por URL (feat/be-e-lector-url). */
    @Column(name = "origen", length = 10)
    private String origen;

    /** Lo calcula feat/be-e-lector-url; en esta rama queda null. */
    @Column(name = "costo_puesto_lima", precision = 12, scale = 2)
    private BigDecimal costoPuestoLima;

    @Column(name = "editado_a_mano", nullable = false)
    @Builder.Default
    private Boolean editadoAMano = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    @Builder.Default
    private EstadoSourcing estado = EstadoSourcing.CAPTURADO;

    /** Se llena al convertir en producto (Fase 3). Solo el id, para no acoplar con Producto aún. */
    @Column(name = "id_producto")
    private Long idProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime creadoEn = LocalDateTime.now();
}
