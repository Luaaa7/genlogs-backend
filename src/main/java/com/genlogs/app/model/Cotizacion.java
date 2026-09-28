package com.genlogs.app.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
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
@Table(name = "cotizacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class Cotizacion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cotizacion")
    private Long idCotizacion;

    @Column(name = "codigo_cotizacion", nullable = false, unique = true, length = 50)
    private String codigoCotizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    // DB: id_contacto es NULLABLE ("NULL = cotización aún sin contacto asignado")
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_contacto")
    private ContactoTercero contacto;

    @Column(name = "fecha_cotizacion", nullable = false)
    private LocalDate fechaCotizacion;

    @Column(name = "fecha_validez", nullable = false)
    private LocalDate fechaValidez;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_vendedor", nullable = false)
    private Usuario usuarioVendedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_cotizacion", nullable = false)
    private EstadoCotizacion estadoCotizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_moneda", nullable = false)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_condicion_pago", nullable = false)
    private CondicionPago condicionPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sector_economico", nullable = false)
    private SectorEconomico sectorEconomico;

    // [ALINEADO A BD V8] La tabla "cotizacion" NO tiene columna "total":
    // los totales (subtotal, IGV 18%, total) se calculan en la vista
    // vw_cotizacion_totales a partir de cotizacion_detalle.importe_linea.
    // Ver CotizacionRepository.findTotalesByCotizacion(...).

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}
