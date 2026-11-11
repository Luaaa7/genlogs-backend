package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    /** Solo el portal de clientes lo usa. Los usuarios internos quedan en NULL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", unique = true)
    private Cliente cliente;

    @Column(name = "nombre_usuario", length = 50, nullable = false, unique = true)
    private String nombreUsuario;

    @Column(name = "nombres", length = 150, nullable = false)
    private String nombres;

    @Column(name = "correo", length = 150, nullable = false, unique = true)
    private String correo;

    @Column(name = "bloqueado", nullable = false)
    private Boolean bloqueado = false;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "intentos_fallidos", nullable = false)
    private Short intentosFallidos = 0;

    @Column(name = "iniciales", length = 3, nullable = false)
    private String iniciales;
}