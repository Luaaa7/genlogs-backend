package com.genlogs.app.model;

import jakarta.persistence.*;
import lombok.*;

/** Documento adjunto de un producto (ficha técnica, manual, certificado, etc.), en Cloudinary. */
@Entity
@Table(name = "documento_producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DocumentoProducto extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Long idDocumento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 30)
    private TipoDocumentoProducto tipoDocumento;

    @Column(name = "nombre_documento", nullable = false, length = 150)
    private String nombreDocumento;

    /** URL https segura devuelta por Cloudinary al subir el documento. */
    @Column(name = "url_documento", nullable = false, columnDefinition = "TEXT")
    private String urlDocumento;
}
