package com.genlogs.app.fuentes.config;

import java.util.List;

import com.genlogs.app.sourcing.ConsultaBusqueda;

/**
 * Puerta única que usa {@code ConsultaFuenteService} para buscar o abrir una ficha
 * con la sesión de la tienda. En la aplicación esa puerta es
 * {@link LectorFuenteDespachador}. Los lectores de cada tienda no implementan
 * esta interfaz: implementan {@link LectorFuenteEspecifico}, para que Spring
 * no vea varios candidatos ni el despachador se inyecte a sí mismo.
 * No registra ni cambia el estado de la fuente.
 */
public interface LectorFuente {

    ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                            ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores);

    ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                             List<SelectorFuenteDto> selectores);
}
