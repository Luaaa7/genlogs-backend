package com.genlogs.app.sourcing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pedido de búsqueda del panel de FE-D. Marca + N.º de parte es lo principal
 * (D-01); GTIN y palabras son de apoyo.
 *
 * @param idSolicitudItem ítem de la solicitud al que se ligan las referencias (opcional)
 * @param forzar          true = consultar las fuentes aunque haya una búsqueda reciente
 */
public record BuscarReferenciasRequest(
        Long idSolicitudItem,
        @NotBlank(message = "El N.º de parte es obligatorio")
        @Size(max = 60) String numeroParte,
        @Size(max = 100) String marca,
        @Pattern(regexp = "^[0-9]{8,14}$", message = "El GTIN debe tener de 8 a 14 dígitos")
        String gtin,
        String palabras,
        boolean forzar) {
}
