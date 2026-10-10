package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** El archivo ya fue subido a Cloudinary (POST /api/archivos/upload); aquí solo se asocia su URL. */
@Getter
@Setter
public class ImagenAdjuntaRequest {

    @NotBlank(message = "La URL de la imagen es obligatoria")
    private String urlImagen;

    private Boolean esPrincipal = false;
}
