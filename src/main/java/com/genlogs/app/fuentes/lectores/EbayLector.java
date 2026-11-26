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
 * ME (v3.3) - Lector de eBay. Si SERPAPI_KEY esta configurada, la busqueda va por SerpApi.
 * Si no, usa el servicio de lectura como antes. EbayFuente (Browse API) no se toca.
 */
@Component
public class EbayLector implements LectorFuenteEspecifico {

    static final String TIENDA = "eBay";
    static final String URL_BASE = "https://www.ebay.com";
    static final String MONEDA = "USD";
    static final String SEPARADOR_DECIMAL = ".";
    static final int MAX_RESULTADOS = 8;
    static final String SIN_RESULTADOS = ".srp-save-null-search";
    static final String BLOQUEO = ".pgHeading > h1";
    static final String VERIFICACION = "#captcha_loading, #captcha_form, form[action*=captcha]";

    private final LectorRemotoClient cliente;
    private final Clock reloj;
    private SerpApiCliente serpApi;

    @Autowired
    public EbayLector(LectorRemotoClient cliente) {
        this(cliente, Clock.systemUTC());
    }

    EbayLector(LectorRemotoClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Autowired(required = false)
    public void setSerpApi(SerpApiCliente serpApi) {
        this.serpApi = serpApi;
    }

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.EBAY);
    }

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        if (codigo != CodigoFuente.EBAY) {
            return ResultadoLectura.error("Este lector solo atiende eBay.", false);
        }
        String texto = PuenteLector.textoDeBusqueda(consulta);
        if (texto == null) {
            return ResultadoLectura.sinResultados();
        }
        if (serpApi != null && serpApi.configurado()) {
            return serpApi.buscar(CodigoFuente.EBAY, texto);
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.BUSQUEDA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaBuscar(reglas)) {
            return ResultadoLectura.error("eBay no tiene reglas de busqueda completas: faltan ITEM, NOMBRE,"
                    + " PRECIO o ENLACE en Configuracion > Fuentes.", false);
        }
        reglas.put("sinResultados", SIN_RESULTADOS);
        agregarAvisos(reglas);
        String url = urlBusqueda(urlBase, texto);
        return PuenteLector.leer(TIENDA, sesion, reloj, s -> cliente.buscar(
                new ContratoLector.Peticion(CodigoFuente.EBAY.name(), url, reglas, s, null, MAX_RESULTADOS)));
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        if (codigo != CodigoFuente.EBAY) {
            return ResultadoLectura.error("Este lector solo atiende eBay.", false);
        }
        if (url == null || url.isBlank()) {
            return ResultadoLectura.error("Falta la URL de la ficha de eBay.", false);
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.FICHA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaFicha(reglas)) {
            return ResultadoLectura.error("eBay no tiene reglas de ficha completas: faltan NOMBRE o PRECIO"
                    + " en Configuracion > Fuentes.", false);
        }
        agregarAvisos(reglas);
        String limpia = url.trim();
        return PuenteLector.leer(TIENDA, sesion, reloj, s -> cliente.leerFicha(
                new ContratoLector.Peticion(CodigoFuente.EBAY.name(), limpia, reglas, s, null, 1)));
    }

    static void agregarAvisos(Map<String, Object> reglas) {
        reglas.put("verificacion", VERIFICACION);
        reglas.put("bloqueo", BLOQUEO);
    }

    static String urlBusqueda(String urlBase, String texto) {
        String base = urlBase == null || urlBase.isBlank() ? URL_BASE : urlBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/sch/i.html?_nkw=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}