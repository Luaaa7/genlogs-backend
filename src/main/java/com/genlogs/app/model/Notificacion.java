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
 * Aviso interno de la campanita (D-21, D-25). Uno por usuario: un aviso "a un rol"
 * se guarda como una fila por cada usuario activo de ese rol, asi cada uno lo
 * marca leido por su cuenta. Los avisos no se borran.
 * Se crean solo con NotificacionService.
 */
@Entity
@Table(name = "notificacion")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    /** Nombre de un TipoAviso. */
    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "mensaje", columnDefinition = "TEXT")
    private String mensaje;

    /** Ruta del frontend a la que lleva el aviso, p. ej. /cotizaciones/15. */
    @Column(name = "enlace", length = 255)
    private String enlace;

    @Column(name = "leida", nullable = false)
    private Boolean leida = false;

    @Column(name = "fecha_lectura")
    private LocalDateTime fechaLectura;

    @Column(name = "correo_enviado", nullable = false)
    private Boolean correoEnviado = false;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}