package com.genlogs.app.sourcing;

import java.math.BigDecimal;

/**
 * Parte del contrato de FuenteBusqueda (Paso 0).
 * Una referencia encontrada en una fuente externa.
 *
 * @param codigoMoneda ISO de 3 letras (USD, PEN); se busca en la tabla moneda
 */
public record ResultadoBusqueda(
        String titulo,
        String marca,
        String numeroParte,
        BigDecimal precio,
        String codigoMoneda,
        String urlReferencia,
        String urlImagen) {
}
