package com.genlogs.app.fuentes.config;

import java.util.List;

import com.genlogs.app.sourcing.ConsultaBusqueda;

/** Respuesta por defecto mientras el servicio de lectura (P2) no está conectado al backend. */
public class LectorFuenteNoConectado implements LectorFuente {

    public static final String MENSAJE =
            "El servicio de lectura todavía no está conectado; no se consulta la tienda.";

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        return ResultadoLectura.noConectado(MENSAJE);
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        return ResultadoLectura.noConectado(MENSAJE);
    }
}
