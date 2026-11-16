package com.genlogs.app.fuentes.config;

import java.util.List;
import java.util.Set;

import com.genlogs.app.sourcing.ConsultaBusqueda;

/**
 * Lector de una o varias tiendas. Karla, Mell y Ale lo registran como bean de Spring.
 * No implementa {@link LectorFuente}: ese tipo es solo el despachador, para que
 * {@code ConsultaFuenteService} tenga un único candidato y el despachador no se
 * reciba a sí mismo.
 *
 * <p>Un mismo bean puede atender varias fuentes ({@code MERCADO_LIBRE} y {@code ALIBABA},
 * o {@code AMAZON} y {@code ALIEXPRESS}). Cada {@link CodigoFuente} admite un solo bean;
 * si dos beans declaran el mismo código, el contexto no arranca.
 */
public interface LectorFuenteEspecifico {

    /**
     * Tiendas que atiende este bean. Vacío o nulo es un error de configuración.
     * El despachador lee este conjunto una vez, al crear el contexto.
     */
    Set<CodigoFuente> fuentes();

    ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                            ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores);

    ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                             List<SelectorFuenteDto> selectores);
}
