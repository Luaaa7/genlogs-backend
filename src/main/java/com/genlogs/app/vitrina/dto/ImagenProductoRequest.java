package com.genlogs.app.vitrina.dto;

import com.genlogs.app.vitrina.FuenteImagen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Foto propia ya subida (Cloudinary). Al registrarla, quien la sube confirma que
 * es real y del repuesto: queda verificada (RF-79). Nunca fotos de las tiendas.
 */
public record ImagenProductoRequest(
        @NotBlank(message = "Falta la URL de la imagen")
        @Pattern(regexp = "^https://\\S+$", message = "La imagen debe ser una URL https")
        String urlImagen,
        @NotNull(message = "Indica de dónde es la foto: recepción, proveedor o cliente") FuenteImagen fuente,
        boolean principal) {
}
