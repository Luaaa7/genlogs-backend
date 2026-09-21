package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class Auditable {

    @Column(name = "user_create", length = 50, nullable = false, updatable = false)
    private String userCreate;

    @Column(name = "process_create", length = 100, nullable = false, updatable = false)
    private String processCreate;

    // La BD tiene DEFAULT CURRENT_TIMESTAMP, pero la seteamos también desde
    // Java (en el @PrePersist de cada service o aquí mismo) para no
    // depender de que Hibernate relea la fila tras el INSERT.
    @Column(name = "date_create", nullable = false, updatable = false)
    private LocalDateTime dateCreate = LocalDateTime.now();

    @Column(name = "user_update", length = 50)
    private String userUpdate;

    @Column(name = "process_update", length = 100)
    private String processUpdate;

    @Column(name = "date_update")
    private LocalDateTime dateUpdate;

    /** 'A' = Activo, 'I' = Inactivo (eliminación lógica) */
    @Column(name = "status", length = 1, nullable = false)
    private String status = "A";
}

