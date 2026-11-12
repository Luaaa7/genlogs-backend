package com.genlogs.app.model;

import java.math.BigDecimal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "solicitud_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class SolicitudItem extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_item")
    private Long idSolicitudItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudCotizacion solicitud;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "unidad", length = 30)
    private String unidad;

    /** Marca del catálogo; si no está, marcaTexto (D-01). */
    @Column(name = "id_marca")
    private Integer idMarca;

    @Column(name = "marca_texto", length = 100)
    private String marcaTexto;

    @Column(name = "numero_parte", length = 60)
    private String numeroParte;

    @Column(name = "gtin", length = 14)
    private String gtin;

    @Column(name = "url_foto", columnDefinition = "TEXT")
    private String urlFoto;

    /** Producto de la vitrina cuando el pedido viene de "Cotizar". */
    @Column(name = "id_producto")
    private Long idProducto;

    /** El Asistente Logístico confirmó o corrigió marca y N.º de parte (D-01). */
    @Column(name = "parte_confirmada", nullable = false)
    @Builder.Default
    private Boolean parteConfirmada = false;

    @Column(name = "id_usuario_confirma")
    private Long idUsuarioConfirma;

    @Column(name = "fecha_confirmacion")
    private java.time.LocalDateTime fechaConfirmacion;

    /** N.º de parte equivalentes que agrega el asistente antes de buscar. Solo interno. */
    @EqualsAndHashCode.Exclude
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "solicitud_item_equivalente", joinColumns = @JoinColumn(name = "id_solicitud_item"))
    @Column(name = "numero_parte", length = 60, nullable = false)
    @Builder.Default
    private java.util.Set<String> equivalentes = new java.util.LinkedHashSet<>();
}
