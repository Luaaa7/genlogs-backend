package com.genlogs.app.model;

import com.genlogs.app.vitrina.FuenteImagen;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;

/** Imagen de un producto (subida a Cloudinary); a lo más una puede ser la principal. */
@Entity
@Table(name = "producto_imagen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ProductoImagen extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_imagen")
    private Long idProductoImagen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    /** URL https segura devuelta por Cloudinary al subir la imagen. */
    @Column(name = "url_imagen", nullable = false, columnDefinition = "TEXT")
    private String urlImagen;

    @Column(name = "es_principal", nullable = false)
    @Builder.Default
    private Boolean esPrincipal = false;

    // ---- v3.2 · Imagen verificada (BE-E, RF-79) ----

    /** De dónde salió la foto. Las imágenes de tiendas nunca se guardan aquí. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fuente_imagen", length = 10)
    private FuenteImagen fuenteImagen;

    /** Quién confirmó que la foto es propia y corresponde al repuesto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificada_por")
    private Usuario verificadaPor;

    /** Con valor = imagen verificada: solo estas se publican. */
    @Column(name = "verificada_en")
    private LocalDateTime verificadaEn;

    public boolean estaVerificada() {
        return verificadaEn != null && fuenteImagen != null && "A".equals(getStatus());
    }
}
