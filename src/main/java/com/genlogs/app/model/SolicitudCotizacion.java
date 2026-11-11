package com.genlogs.app.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Pedido que el cliente envía desde el portal. id_cotizacion se llena al convertirla. */
@Entity
@Table(name = "solicitud_cotizacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class SolicitudCotizacion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    /** Vacío hasta que el equipo interno la convierta en cotización. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cotizacion")
    private Cotizacion cotizacion;

    /** Formato SOL-AAAA-n, como ck_solicitud_cotizacion_codigo. */
    @Column(name = "codigo_solicitud", nullable = false, unique = true, length = 20)
    private String codigoSolicitud;

    @Column(name = "asunto", nullable = false, length = 150)
    private String asunto;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    /** PENDIENTE, EN_REVISION, COTIZADA, RECHAZADA o CANCELADA. */
    @Column(name = "estado_solicitud", nullable = false, length = 20)
    @Builder.Default
    private String estadoSolicitud = "PENDIENTE";

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SolicitudItem> items = new ArrayList<>();

    public void agregarItem(SolicitudItem item) {
        item.setSolicitud(this);
        items.add(item);
    }
}
