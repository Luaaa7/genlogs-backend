package com.genlogs.app.sourcing;

import java.math.BigDecimal;

/**
 * PROVISIONAL — viene con FuenteBusqueda en el Paso 0. No va en el PR.
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
