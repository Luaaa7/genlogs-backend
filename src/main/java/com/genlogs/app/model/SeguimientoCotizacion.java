package com.genlogs.app.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
@Table(name = "seguimiento_cotizacion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false)
public class SeguimientoCotizacion extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_seguimiento")
    private Long idSeguimiento;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cotizacion", nullable = false)
    private Cotizacion cotizacion;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_cotizacion", nullable = false)
    private EstadoCotizacion estadoCotizacion;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;
    
    @Column(name = "fecha_evento", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT NOW()")
    private LocalDateTime fechaEvento;
    
    @Column(name = "comentario", columnDefinition = "TEXT")
    private String comentario;

    /** Estado previo al evento. Nulo en filas anteriores a v3_4. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_anterior")
    private EstadoCotizacion estadoAnterior;

    /** Día en que el cliente respondió (lo informa quien registra). */
    @Column(name = "fecha_respuesta")
    private LocalDate fechaRespuesta;

    /** correo, whatsapp, llamada u otro. Nulo si el evento no es una respuesta. */
    @Column(name = "medio", length = 20)
    private String medio;

    /** URL del archivo de evidencia, si se adjuntó. */
    @Column(name = "ruta_adjunto", columnDefinition = "TEXT")
    private String rutaAdjunto;
}
