package com.genlogs.app.fuentes.lectores;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.LectorFuenteEspecifico;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;

/**
 * ME (v3.3) · Lector de Amazon y AliExpress para el despachador multifuente (BPMN 9-B.1).
 *
 * <p>Recibe la sesión ya lista y los selectores de fuente_selector, arma la URL de
 * búsqueda de la tienda, llama al servicio de lectura y devuelve un {@link ResultadoLectura}.
 * No pide sesión, no marca estados y no guarda nada: eso lo hace ConsultaFuenteService.</p>
 */
@Component
public class AmazonAliExpressLector implements LectorFuenteEspecifico {

    static final String URL_AMAZON = "https://www.amazon.com";
    static final String URL_ALIEXPRESS = "https://www.aliexpress.com";
    /**
     * Respaldo cuando la página no trae moneda. Con la sesión en Perú, Amazon muestra PEN
     * y AliExpress muestra S/; fuera de Perú, AliExpress muestra US $. El decimal es el punto.
     */
    static final String MONEDA = "USD";
    static final String SEPARADOR_DECIMAL = ".";
    static final int MAX_RESULTADOS = 8;
    /** Bloque del aviso de búsqueda vacía. .s-no-outline también envuelve enlaces de una búsqueda con productos. */
    static final String SIN_RESULTADOS_AMAZON = "[data-csa-c-content-id=\"messaging-messages-no-results\"]";
    /**
     * Revisado en la búsqueda de «oil filter» y en una de texto inventado: no aparece.
     * Esa segunda página no está vacía. Una extracción rota sigue en error.
     */
    static final String SIN_RESULTADOS_ALIEXPRESS = ".search-no-result";

    private final LectorRemotoClient cliente;
    private final Clock reloj;

    @Autowired
    public AmazonAliExpressLector(LectorRemotoClient cliente) {
        this(cliente, Clock.systemUTC());
    }

    AmazonAliExpressLector(LectorRemotoClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.AMAZON, CodigoFuente.ALIEXPRESS);
    }

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        if (!atiende(codigo)) {
            return ResultadoLectura.error("Este lector solo atiende Amazon y AliExpress.", false);
        }
        String texto = PuenteLector.textoDeBusqueda(consulta);
        if (texto == null) {
            return ResultadoLectura.sinResultados();
        }
        String tienda = tienda(codigo);
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.BUSQUEDA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaBuscar(reglas)) {
            return ResultadoLectura.error(tienda + " no tiene reglas de búsqueda completas: faltan ITEM, NOMBRE,"
                    + " PRECIO o ENLACE en Configuración > Fuentes.", false);
        }
        // Sin esto, una búsqueda sin resultados se tomaría como reglas rotas (CON_ERROR y aviso al Jefe).
        reglas.put("sinResultados", sinResultados(codigo));
        String url = urlBusqueda(codigo, urlBase, texto);
        return PuenteLector.leer(tienda, sesion, reloj, s -> cliente.buscar(
                new ContratoLector.Peticion(codigo.name(), url, reglas, s, null, MAX_RESULTADOS)));
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        if (!atiende(codigo)) {
            return ResultadoLectura.error("Este lector solo atiende Amazon y AliExpress.", false);
        }
        String tienda = tienda(codigo);
        if (url == null || url.isBlank()) {
            return ResultadoLectura.error("Falta la URL de la ficha de " + tienda + ".", false);
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.FICHA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaFicha(reglas)) {
            return ResultadoLectura.error(tienda + " no tiene reglas de ficha completas: faltan NOMBRE o PRECIO"
                    + " en Configuración > Fuentes.", false);
        }
        String limpia = url.trim();
        return PuenteLector.leer(tienda, sesion, reloj, s -> cliente.leerFicha(
                new ContratoLector.Peticion(codigo.name(), limpia, reglas, s, null, 1)));
    }

    /** Amazon: /s?k=. AliExpress: /wholesale?SearchText=. La base sale de fuente_busqueda.url_base. */
    static String urlBusqueda(CodigoFuente codigo, String urlBase, String texto) {
        String defecto = codigo == CodigoFuente.AMAZON ? URL_AMAZON : URL_ALIEXPRESS;
        String base = urlBase == null || urlBase.isBlank() ? defecto : urlBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String camino = codigo == CodigoFuente.AMAZON ? "/s?k=" : "/wholesale?SearchText=";
        return base + camino + URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private static boolean atiende(CodigoFuente codigo) {
        return codigo == CodigoFuente.AMAZON || codigo == CodigoFuente.ALIEXPRESS;
    }

    private static String tienda(CodigoFuente codigo) {
        return codigo == CodigoFuente.AMAZON ? "Amazon" : "AliExpress";
    }

    private static String sinResultados(CodigoFuente codigo) {
        return codigo == CodigoFuente.AMAZON ? SIN_RESULTADOS_AMAZON : SIN_RESULTADOS_ALIEXPRESS;
    }
}
