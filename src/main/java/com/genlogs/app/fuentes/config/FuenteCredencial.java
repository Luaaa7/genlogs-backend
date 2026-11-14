package com.genlogs.app.fuentes.config;

import com.genlogs.app.model.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario, clave o sesión de una tienda (tabla fuente_credencial, v3_3.sql).
 * Solo guarda el valor cifrado con AES-GCM; para leer o escribir el valor se usa
 * CredencialesFuenteService, nunca este campo directo. No tiene toString para que
 * el valor no termine en un log.
 */
@Entity
@Table(name = "fuente_credencial",
        uniqueConstraints = @UniqueConstraint(name = "uq_fuente_credencial_tipo", columnNames = {"id_fuente", "tipo"}))
@Getter
@Setter
@NoArgsConstructor
public class FuenteCredencial extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credencial")
    private Long idCredencial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    private FuenteBusquedaConfig fuente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoCredencial tipo;

    /** Cifrado por CifradoFuentes. Sin getter público: se descifra solo en el servicio. */
    @Getter(AccessLevel.PACKAGE)
    @Column(name = "valor_cifrado", nullable = false, columnDefinition = "TEXT")
    private String valorCifrado;

    @Column(name = "editado_por")
    private Long editadoPor;
}
