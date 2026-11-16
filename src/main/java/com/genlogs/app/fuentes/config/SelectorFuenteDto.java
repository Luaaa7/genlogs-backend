package com.genlogs.app.fuentes.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Una regla de extracción (página + campo + regla CSS o XPath) de una tienda. */
public record SelectorFuenteDto(
        @NotNull(message = "Indica la página") PaginaSelector pagina,
        @NotNull(message = "Indica el campo") CampoSelector campo,
        @NotBlank(message = "La regla no puede estar vacía")
        @Size(max = 1000, message = "La regla no puede pasar de 1000 caracteres")
        String regla) {
}
