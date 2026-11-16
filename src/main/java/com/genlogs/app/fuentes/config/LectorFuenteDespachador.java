package com.genlogs.app.fuentes.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.genlogs.app.sourcing.ConsultaBusqueda;

/**
 * Único {@link LectorFuente} que recibe {@code ConsultaFuenteService}.
 * Elige el {@link LectorFuenteEspecifico} según {@link CodigoFuente} y, si esa
 * tienda todavía no tiene lector, responde {@link ResultadoLectura.Tipo#NO_CONECTADO}
 * sin tocar la sesión ni la cuota.
 *
 * <p>No inyecta {@code List<LectorFuente>}: eso lo incluiría a sí mismo.
 * Solo recibe beans de {@link LectorFuenteEspecifico}.
 */
@Component(LectorFuenteDespachador.BEAN)
@Primary
public class LectorFuenteDespachador implements LectorFuente {

    public static final String BEAN = "lectorFuenteDespachador";

    static final String MENSAJE_SIN_LECTOR =
            "No hay un lector registrado para %s; no se consulta la tienda.";

    private final Map<CodigoFuente, LectorFuenteEspecifico> porFuente;

    @Autowired
    public LectorFuenteDespachador(ObjectProvider<LectorFuenteEspecifico> lectores) {
        this(lectores.orderedStream().toList());
    }

    LectorFuenteDespachador(List<LectorFuenteEspecifico> lectores) {
        Map<CodigoFuente, LectorFuenteEspecifico> mapa = new EnumMap<>(CodigoFuente.class);
        if (lectores != null) {
            for (LectorFuenteEspecifico lector : lectores) {
                if (lector == null) {
                    throw new IllegalStateException("La lista de lectores específicos contiene un nulo.");
                }
                Set<CodigoFuente> fuentes = lector.fuentes();
                if (fuentes == null || fuentes.isEmpty()) {
                    throw new IllegalStateException(
                            "El lector " + nombre(lector) + " no declaró ninguna CodigoFuente.");
                }
                for (CodigoFuente codigo : fuentes) {
                    if (codigo == null) {
                        throw new IllegalStateException(
                                "El lector " + nombre(lector) + " declaró un CodigoFuente nulo.");
                    }
                    LectorFuenteEspecifico previo = mapa.put(codigo, lector);
                    if (previo != null && previo != lector) {
                        throw new IllegalStateException(
                                "Dos lectores registrados para " + codigo + ": "
                                        + nombre(previo) + " y " + nombre(lector)
                                        + ". Cada CodigoFuente admite un solo lector.");
                    }
                }
            }
        }
        this.porFuente = Map.copyOf(mapa);
    }

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        LectorFuenteEspecifico lector = lectorDe(codigo);
        if (lector == null) {
            return sinLector(codigo);
        }
        return lector.buscar(codigo, urlBase, sesion, consulta, selectores);
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        LectorFuenteEspecifico lector = lectorDe(codigo);
        if (lector == null) {
            return sinLector(codigo);
        }
        return lector.leerUrl(codigo, url, sesion, selectores);
    }

    LectorFuenteEspecifico lectorDe(CodigoFuente codigo) {
        return codigo == null ? null : porFuente.get(codigo);
    }

    private static ResultadoLectura sinLector(CodigoFuente codigo) {
        String cual = codigo == null ? "la tienda solicitada" : codigo.name();
        return ResultadoLectura.noConectado(MENSAJE_SIN_LECTOR.formatted(cual));
    }

    private static String nombre(LectorFuenteEspecifico lector) {
        return lector.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(lector));
    }
}
