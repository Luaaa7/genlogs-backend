package com.genlogs.app.dto;

import com.genlogs.app.model.TipoDocumentoProducto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** El archivo ya fue subido a Cloudinary (POST /api/archivos/upload); aquí solo se asocia su URL. */
@Getter
@Setter
public class DocumentoAdjuntoRequest {

    @NotNull(message = "El tipo de documento es obligatorio")
    private TipoDocumentoProducto tipoDocumento;

    @NotBlank(message = "El nombre del documento es obligatorio")
    private String nombreDocumento;

    @NotBlank(message = "La URL del documento es obligatoria")
    private String urlDocumento;
}