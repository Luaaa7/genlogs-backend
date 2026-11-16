package com.genlogs.app.fuentes.lectores;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
import com.genlogs.app.sourcing.lector.LectorRemotoException;

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
    static final String MONEDA = "USD";
    static final String SEPARADOR_DECIMAL = ".";
    static final int MAX_RESULTADOS = 8;
    /** Bloque de Amazon cuando la búsqueda no encuentra nada ("No results for"). */
    static final String SIN_RESULTADOS_AMAZON = ".s-no-outline";
    /** Bloque de AliExpress cuando la búsqueda mayorista no encuentra nada. */
    static final String SIN_RESULTADOS_ALIEXPRESS = ".search-no-result";
    private static final int LARGO_CONSULTA = 200;

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
        String texto = textoDeBusqueda(consulta);
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
        return leer(tienda, sesion, s -> cliente.buscar(
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
        return leer(tienda, sesion, s -> cliente.leerFicha(
                new ContratoLector.Peticion(codigo.name(), limpia, reglas, s, null, 1)));
    }

    private ResultadoLectura leer(String tienda, String sesion,
                                  Function<ContratoLector.Sesion, ContratoLector.Respuesta> llamada) {
        ContratoLector.Sesion s;
        try {
            s = PuenteLector.sesion(sesion, reloj);
        } catch (IllegalArgumentException e) {
            return ResultadoLectura.error("La sesión guardada de " + tienda
                    + " no se puede usar; vuelve a probar la sesión.", false);
        }
        try {
            return PuenteLector.aResultado(llamada.apply(s));
        } catch (LectorRemotoException e) {
            return PuenteLector.deError(tienda, e);
        }
    }

    static String textoDeBusqueda(ConsultaBusqueda consulta) {
        if (consulta == null || consulta.metodo() == null) {
            return null;
        }
        String texto = switch (consulta.metodo()) {
            case NUMERO_PARTE -> unir(consulta.marca(), consulta.numeroParte());
            case GTIN -> consulta.gtin();
            case PALABRAS -> unir(consulta.marca(), consulta.palabras());
            default -> null;
        };
        if (texto == null) {
            return null;
        }
        texto = texto.replaceAll("\\s+", " ").trim();
        if (texto.isEmpty()) {
            return null;
        }
        return texto.length() <= LARGO_CONSULTA ? texto : texto.substring(0, LARGO_CONSULTA).trim();
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

    private static String unir(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return (x + " " + y).trim();
    }
}
