package com.genlogs.app.fuentes.config;

import java.time.LocalDateTime;

import com.genlogs.app.model.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una tienda de la búsqueda por scraping (tabla fuente_busqueda, v3_3.sql).
 * Se llama FuenteBusquedaConfig porque FuenteBusqueda ya es la interfaz de los
 * lectores (paquete sourcing). Las columnas son exactamente las del script
 * (producción usa ddl-auto=validate).
 */
@Entity
@Table(name = "fuente_busqueda")
@Getter
@Setter
@NoArgsConstructor
public class FuenteBusquedaConfig extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fuente")
    private Integer idFuente;

    @Enumerated(EnumType.STRING)
    @Column(name = "codigo", nullable = false, unique = true, length = 15)
    private CodigoFuente codigo;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "url_base", nullable = false, length = 255)
    private String urlBase;

    /** La enciende el Jefe Administrativo, solo si pasó la prueba de 10 pedidos. */
    @Column(name = "activa", nullable = false)
    private Boolean activa = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoFuente estado = EstadoFuente.SIN_PROBAR;

    @Column(name = "sesion_valida_hasta")
    private LocalDateTime sesionValidaHasta;

    @Column(name = "ultima_prueba")
    private LocalDateTime ultimaPrueba;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "pausa_segundos", nullable = false)
    private Integer pausaSegundos = 5;

    @Column(name = "limite_diario", nullable = false)
    private Integer limiteDiario = 50;

    /** Aciertos de la última prueba de 10 pedidos (0 a 10); null si no se probó. */
    @Column(name = "aciertos_prueba")
    private Short aciertosPrueba;

    @Column(name = "editado_por")
    private Long editadoPor;
}
