package com.genlogs.app.sourcing;

import java.util.List;

/**
 * Contrato de una fuente externa de busqueda (Paso 0). Cada conexion (eBay,
 * Mercado Libre y, si pasan la prueba, Amazon y Alibaba) la implementa en el
 * paquete "fuentes" como un @Component; SourcingService recibe todas las que
 * Spring encuentre. Si no hay ninguna, la busqueda sigue con la captura por URL.
 */
public interface FuenteBusqueda {

    /** Código que se guarda en historial_sourcing.fuente: EBAY, MERCADO_LIBRE, ALIBABA. */
    String codigo();

    /** Busca referencias. Nunca devuelve null; si no hay resultados, lista vacía. */
    List<ResultadoBusqueda> buscar(ConsultaBusqueda consulta);
}
