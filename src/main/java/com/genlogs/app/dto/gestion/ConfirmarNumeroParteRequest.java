package com.genlogs.app.dto.gestion;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El asistente confirma o corrige la identificación del repuesto (D-01): marca (del catálogo
 * o en texto), N.º de parte del fabricante, GTIN opcional y hasta 10 N.º equivalentes.
 */
public record ConfirmarNumeroParteRequest(
        Integer idMarca,
        @Size(max = 100, message = "La marca puede tener hasta 100 caracteres")
        String marcaTexto,
        @NotBlank(message = "Escribe el N.º de parte")
        @Size(max = 60, message = "El N.º de parte puede tener hasta 60 caracteres")
        String numeroParte,
        @Pattern(regexp = "^$|^\\d{8,14}$", message = "El GTIN debe tener entre 8 y 14 dígitos")
        String gtin,
        @Size(max = 10, message = "Agrega hasta 10 N.º equivalentes")
        List<@NotBlank @Size(max = 60, message = "Cada equivalente puede tener hasta 60 caracteres") String> equivalentes) {
}
