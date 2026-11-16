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
 * ME (v3.3) · Lector de eBay para el despachador multifuente (BPMN 9-B.1).
 *
 * <p>Recibe la sesión ya lista y los selectores de fuente_selector, arma la URL de
 * búsqueda de eBay, llama al servicio de lectura y devuelve un {@link ResultadoLectura}.
 * No pide sesión, no marca estados y no guarda nada: eso lo hace ConsultaFuenteService.</p>
 *
 * <p>EbayFuente (Browse API) no se toca: sigue como respaldo de FuenteBusqueda.</p>
 */
@Component
public class EbayLector implements LectorFuenteEspecifico {

    static final String TIENDA = "eBay";
    static final String URL_BASE = "https://www.ebay.com";
    static final String MONEDA = "USD";
    static final String SEPARADOR_DECIMAL = ".";
    static final int MAX_RESULTADOS = 8;
    /** Bloque que muestra eBay cuando la búsqueda no encuentra nada ("No se ha encontrado ninguna coincidencia exacta"). */
    static final String SIN_RESULTADOS = ".srp-save-null-search";
    /**
     * Página "El acceso al sitio web está bloqueado" (ebay.com/splashui/mitigation?page=drop).
     * Tomado del HTML real (16/11/2026): el título va en div.pgHeading > h1 y la búsqueda no lo usa.
     */
    static final String BLOQUEO = ".pgHeading > h1";
    /**
     * Página de verificación de eBay (splashui/challenge, "Checking your browser").
     * Por confirmar con un captcha real: eBay no deja abrirla directo. El servicio de lectura
     * además detecta por su cuenta reCAPTCHA, hCaptcha y títulos con "captcha".
     */
    static final String VERIFICACION = "#captcha_loading, #captcha_form, form[action*=captcha]";
    private static final int LARGO_CONSULTA = 200;

    private final LectorRemotoClient cliente;
    private final Clock reloj;

    @Autowired
    public EbayLector(LectorRemotoClient cliente) {
        this(cliente, Clock.systemUTC());
    }

    EbayLector(LectorRemotoClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
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
        String texto = textoDeBusqueda(consulta);
        if (texto == null) {
            return ResultadoLectura.sinResultados();
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.BUSQUEDA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaBuscar(reglas)) {
            return ResultadoLectura.error("eBay no tiene reglas de búsqueda completas: faltan ITEM, NOMBRE,"
                    + " PRECIO o ENLACE en Configuración > Fuentes.", false);
        }
        // Sin esto, una búsqueda sin resultados se tomaría como reglas rotas (CON_ERROR y aviso al Jefe).
        reglas.put("sinResultados", SIN_RESULTADOS);
        agregarAvisos(reglas);
        String url = urlBusqueda(urlBase, texto);
        return leer(sesion, s -> cliente.buscar(
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
                    + " en Configuración > Fuentes.", false);
        }
        agregarAvisos(reglas);
        String limpia = url.trim();
        return leer(sesion, s -> cliente.leerFicha(
                new ContratoLector.Peticion(CodigoFuente.EBAY.name(), limpia, reglas, s, null, 1)));
    }

    /**
     * Para que el servicio de lectura distinga un captcha (REQUIERE_VERIFICACION) o un bloqueo
     * (BLOQUEADA) de eBay de una página sin resultados o con reglas rotas.
     */
    static void agregarAvisos(Map<String, Object> reglas) {
        reglas.put("verificacion", VERIFICACION);
        reglas.put("bloqueo", BLOQUEO);
    }

    /** Llama al servicio de lectura con la sesión y traduce la respuesta o el error. */
    private ResultadoLectura leer(String sesion, Function<ContratoLector.Sesion, ContratoLector.Respuesta> llamada) {
        ContratoLector.Sesion s;
        try {
            s = PuenteLector.sesion(sesion, reloj);
        } catch (IllegalArgumentException e) {
            return ResultadoLectura.error("La sesión guardada de eBay no se puede usar; vuelve a probar la sesión.",
                    false);
        }
        try {
            return PuenteLector.aResultado(llamada.apply(s));
        } catch (LectorRemotoException e) {
            return PuenteLector.deError(TIENDA, e);
        }
    }

    /** Qué se escribe en el buscador de eBay; null si no hay nada que buscar (o es búsqueda por imagen). */
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

    /** https://www.ebay.com/sch/i.html?_nkw=CAT+1R-0750 (la base sale de fuente_busqueda.url_base). */
    static String urlBusqueda(String urlBase, String texto) {
        String base = urlBase == null || urlBase.isBlank() ? URL_BASE : urlBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/sch/i.html?_nkw=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private static String unir(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return (x + " " + y).trim();
    }
}