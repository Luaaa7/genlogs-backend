package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.util.List;

import com.genlogs.app.enums.CanalSolicitud;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato del endpoint unico de solicitudes (Paso 0): POST /api/solicitudes.
 * Portal, landing (despues del registro, D-16 y D-30) y vitrina ("Cotizar")
 * crean la solicitud con este mismo cuerpo. Lo implementa BE-B
 * (feat/be-b-endpoint-solicitudes). Espejo en el frontend: src/api/contratos/solicitudes.ts.
 *
 * El dato principal del repuesto es marca + N. de parte (D-01); el GTIN es opcional.
 */
@Getter
@Setter
public class SolicitudRequest {

    @NotNull(message = "Indica desde dónde se envía la solicitud")
    private CanalSolicitud canal;

    @NotBlank(message = "El asunto es obligatorio")
    @Size(max = 150, message = "El asunto es demasiado largo")
    private String asunto;

    @Size(max = 4000, message = "La descripción es demasiado larga")
    private String descripcion;

    @NotEmpty(message = "Agrega al menos un repuesto")
    @Size(max = 50, message = "Una solicitud admite hasta 50 repuestos")
    private List<@Valid Item> items;

    @Getter
    @Setter
    public static class Item {

        /** Marca del catalogo (opcional). */
        private Integer idMarca;

        /** Marca escrita por el cliente si no esta en el catalogo. */
        @Size(max = 100, message = "La marca es demasiado larga")
        private String marcaTexto;

        /** N. de parte del fabricante (P/N). */
        @Size(max = 60, message = "El N.º de parte es demasiado largo")
        private String numeroParte;

        @Pattern(regexp = "^[0-9]{8,14}$", message = "El GTIN debe tener de 8 a 14 dígitos")
        private String gtin;

        @NotBlank(message = "Describe el repuesto")
        @Size(max = 255, message = "La descripción del repuesto es demasiado larga")
        private String descripcion;

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2, message = "La cantidad tiene demasiados dígitos")
        private BigDecimal cantidad;

        @Size(max = 30, message = "La unidad es demasiado larga")
        private String unidad;

        /** Foto o plano ya subido (URL de Cloudinary). */
        private String urlFoto;

        /** Producto de la vitrina, si viene de "Cotizar" (opcional). */
        private Long idProducto;
    }
}