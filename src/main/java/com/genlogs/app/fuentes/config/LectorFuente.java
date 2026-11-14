package com.genlogs.app.fuentes.config;

import java.util.List;

import com.genlogs.app.sourcing.ConsultaBusqueda;

/**
 * Puerta hacia el servicio de lectura (P1/P2) para buscar o abrir una ficha con la sesión
 * de la tienda. La implementación real la conecta Persona 6; mientras tanto
 * {@link LectorFuenteNoConectado}. No registra ni cambia el estado de la fuente.
 */
public interface LectorFuente {

    ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                            ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores);

    ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                             List<SelectorFuenteDto> selectores);
}
