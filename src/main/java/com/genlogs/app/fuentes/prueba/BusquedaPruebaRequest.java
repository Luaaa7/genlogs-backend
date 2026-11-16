package com.genlogs.app.fuentes.prueba;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un pedido real de la prueba: marca y N.º de parte (D-01). */
public record BusquedaPruebaRequest(
        @Size(max = 60, message = "La marca no puede pasar de 60 caracteres")
        String marca,
        @NotBlank(message = "Indica el N.º de parte")
        @Size(max = 60, message = "El N.º de parte no puede pasar de 60 caracteres")
        String numeroParte) {
}