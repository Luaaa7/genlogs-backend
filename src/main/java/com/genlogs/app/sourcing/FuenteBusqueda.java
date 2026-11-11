package com.genlogs.app.sourcing;

import java.util.List;

/**
 * PROVISIONAL — esta interfaz la sube Mell en el Paso 0 (chore/paso-0).
 * NO se incluye en el PR de feat/be-e-historial: cuando el Paso 0 esté en
 * release/v3.0, borra este archivo, haz rebase y ajusta SourcingService si la
 * firma oficial cambia.
 *
 * Cada fuente externa (eBay, Mercado Libre, Alibaba) la implementa Mell en su
 * paquete "fuentes". SourcingService recibe todas las que Spring encuentre.
 */
public interface FuenteBusqueda {

    /** Código que se guarda en historial_sourcing.fuente: EBAY, MERCADO_LIBRE, ALIBABA. */
    String codigo();

    /** Busca referencias. Nunca devuelve null; si no hay resultados, lista vacía. */
    List<ResultadoBusqueda> buscar(ConsultaBusqueda consulta);
}
