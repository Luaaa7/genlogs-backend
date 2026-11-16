package com.genlogs.app.fuentes.lectores;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
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
 * KM (v3.3) · Lector de Mercado Libre para el despachador multifuente (BPMN 9-B, parte de Karla).
 *
 * <p>Recibe la sesión y los selectores ya listos, arma la URL de búsqueda por marca + N.º de parte,
 * llama al servicio de lectura y devuelve un {@link ResultadoLectura}. No pide sesión, no marca
 * estados y no guarda nada: el límite de 8 fichas, el historial y marcarVerificacion los hace el
 * 9-B.1 (ConsultaFuenteService).</p>
 *
 * <p>Solo datos (D-36): nunca se mandan credenciales ni se lee stock ni la cuenta de vendedor.
 * Un resultado sin precio queda vacío, nunca en 0 (lo hacen el servicio de lectura y NormalizadorFicha).</p>
 */
@Component
public class MercadoLibreLector implements LectorFuenteEspecifico {

    static final String TIENDA = "Mercado Libre";
    static final String URL_BASE = "https://www.mercadolibre.com.pe";
    /** Mercado Libre Perú publica en soles; fuente_busqueda.url_base es el sitio .com.pe (v3_3.sql). */
    static final String MONEDA = "PEN";
    static final String SEPARADOR_DECIMAL = ".";
    static final int MAX_RESULTADOS = 8;
    /**
     * Bloque "No hay publicaciones que coincidan con tu búsqueda" del listado.
     * Por confirmar cuando funcione «Probar sesión» (Luana): sin sesión, Mercado Libre no deja ver el listado.
     */
    static final String SIN_RESULTADOS = ".ui-search-rescue";
    /**
     * Página de verificación de cuenta de Mercado Libre: sin sesión, el listado redirige a
     * /gz/account-verification ("¡Hola! Para continuar, ingresa a tu cuenta"). Tomado del HTML real
     * (16/11/2026): su contenido va en div.account-verification-main, que el listado no usa.
     * Los otros tres son captchas genéricos; el servicio de lectura además detecta por su cuenta
     * reCAPTCHA, hCaptcha y títulos con "captcha". El bloqueo no se manda hasta tener el HTML real:
     * sin él, una página bloqueada sale como error y nunca como "sin resultados".
     */
    static final String VERIFICACION = ".account-verification-main, form[action*=captcha], .g-recaptcha, #captcha";
    private static final int LARGO_CONSULTA = 200;

    private final LectorRemotoClient cliente;
    private final Clock reloj;

    @Autowired
    public MercadoLibreLector(LectorRemotoClient cliente) {
        this(cliente, Clock.systemUTC());
    }

    MercadoLibreLector(LectorRemotoClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.MERCADO_LIBRE);
    }

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        if (codigo != CodigoFuente.MERCADO_LIBRE) {
            return ResultadoLectura.error("Este lector solo atiende Mercado Libre.", false);
        }
        String texto = textoDeBusqueda(consulta);
        if (texto == null) {
            return ResultadoLectura.sinResultados();
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.BUSQUEDA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaBuscar(reglas)) {
            return ResultadoLectura.error("Mercado Libre no tiene reglas de búsqueda completas: faltan ITEM,"
                    + " NOMBRE, PRECIO o ENLACE en Configuración > Fuentes.", false);
        }
        // Sin esto, una búsqueda sin resultados se tomaría como reglas rotas (CON_ERROR y aviso al Jefe).
        reglas.put("sinResultados", SIN_RESULTADOS);
        agregarAvisos(reglas);
        String url = urlBusqueda(urlBase, texto);
        return leer(sesion, s -> cliente.buscar(
                new ContratoLector.Peticion(CodigoFuente.MERCADO_LIBRE.name(), url, reglas, s, null, MAX_RESULTADOS)));
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        if (codigo != CodigoFuente.MERCADO_LIBRE) {
            return ResultadoLectura.error("Este lector solo atiende Mercado Libre.", false);
        }
        if (url == null || url.isBlank()) {
            return ResultadoLectura.error("Falta la URL de la ficha de Mercado Libre.", false);
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.FICHA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaFicha(reglas)) {
            return ResultadoLectura.error("Mercado Libre no tiene reglas de ficha completas: faltan NOMBRE o"
                    + " PRECIO en Configuración > Fuentes.", false);
        }
        agregarAvisos(reglas);
        String limpia = url.trim();
        return leer(sesion, s -> cliente.leerFicha(
                new ContratoLector.Peticion(CodigoFuente.MERCADO_LIBRE.name(), limpia, reglas, s, null, 1)));
    }

    /** Para que el servicio de lectura distinga un captcha (REQUIERE_VERIFICACION) de reglas rotas. */
    static void agregarAvisos(Map<String, Object> reglas) {
        reglas.put("verificacion", VERIFICACION);
    }

    /** Llama al servicio de lectura con la sesión y traduce la respuesta o el error. */
    private ResultadoLectura leer(String sesion, Function<ContratoLector.Sesion, ContratoLector.Respuesta> llamada) {
        ContratoLector.Sesion s;
        try {
            s = PuenteLector.sesion(sesion, reloj);
        } catch (IllegalArgumentException e) {
            return ResultadoLectura.error("La sesión guardada de Mercado Libre no se puede usar; vuelve a probar"
                    + " la sesión.", false);
        }
        try {
            return PuenteLector.aResultado(llamada.apply(s));
        } catch (LectorRemotoException e) {
            return PuenteLector.deError(TIENDA, e);
        }
    }

    /** Qué se escribe en el buscador: marca + N.º de parte, GTIN o palabras; null si no hay nada (o es por imagen). */
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

    /**
     * Listado de Mercado Libre: https://listado.mercadolibre.com.pe/cat-1r-0750
     * (las palabras van en minúsculas y unidas con guiones). El dominio sale de fuente_busqueda.url_base.
     */
    static String urlBusqueda(String urlBase, String texto) {
        String host = hostDe(urlBase);
        String sinWww = host.startsWith("www.") ? host.substring(4)
                : host.startsWith("listado.") ? host.substring(8) : host;
        StringBuilder ruta = new StringBuilder();
        for (String palabra : texto.toLowerCase(Locale.ROOT).split(" ")) {
            if (palabra.isBlank()) {
                continue;
            }
            if (ruta.length() > 0) {
                ruta.append('-');
            }
            ruta.append(URLEncoder.encode(palabra, StandardCharsets.UTF_8));
        }
        return "https://listado." + sinWww + "/" + ruta;
    }

    private static String hostDe(String urlBase) {
        String base = urlBase == null || urlBase.isBlank() ? URL_BASE : urlBase.trim();
        try {
            String host = URI.create(base).getHost();
            if (host != null && !host.isBlank()) {
                return host.toLowerCase(Locale.ROOT);
            }
        } catch (IllegalArgumentException e) {
            // url_base mal escrita: se usa la de Mercado Libre Perú.
        }
        return URI.create(URL_BASE).getHost();
    }

    private static String unir(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return (x + " " + y).trim();
    }
}
