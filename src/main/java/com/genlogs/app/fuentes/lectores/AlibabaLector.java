package com.genlogs.app.fuentes.lectores;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FichaFuente;
import com.genlogs.app.fuentes.config.LectorFuenteEspecifico;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import com.genlogs.app.sourcing.lector.LectorRemotoException;

/**
 * KA (v3.3) · Lector de Alibaba para el despachador multifuente (BPMN 9-B, parte de Karla).
 *
 * <p>Igual que el de Mercado Libre: recibe la sesión y los selectores ya listos, arma la URL de
 * búsqueda, llama al servicio de lectura y devuelve un {@link ResultadoLectura}. No pide sesión,
 * no marca estados y no guarda nada (eso lo hace el 9-B.1). Solo datos (D-36).</p>
 *
 * <p>Alibaba muestra rangos de precio ("US$12.50-15.00") y mínimos de compra. Se pide al servicio
 * de lectura que tome el menor del rango (regla precioRango="menor") y el texto del precio tal como
 * aparece se guarda en la descripción, para que el rango no se pierda.</p>
 */
@Component
public class AlibabaLector implements LectorFuenteEspecifico {

    static final String TIENDA = "Alibaba";
    static final String URL_BASE = "https://www.alibaba.com";
    static final String MONEDA = "USD";
    static final String SEPARADOR_DECIMAL = ".";
    static final int MAX_RESULTADOS = 8;
    /** Mensaje de búsqueda sin resultados de Alibaba. Por confirmar con las páginas guardadas de P4. */
    static final String SIN_RESULTADOS = ".search-no-result, .no-result";
    /**
     * Desafío de Alibaba (slider o página "punish"). Por confirmar con un caso real; el servicio de
     * lectura además detecta por su cuenta reCAPTCHA, hCaptcha y títulos con "captcha". El bloqueo
     * no se manda hasta tener el HTML real.
     */
    static final String VERIFICACION = "#nc_1_wrapper, .nc-container, iframe[src*=punish], form[action*=captcha]";
    static final String PREFIJO_RANGO = "Precio en Alibaba por rango: ";
    /** Dos importes separados por "-", "–" o "~": US$12.50-15.00, 12.50 – 15.00. */
    private static final Pattern RANGO = Pattern.compile("\\d[\\d.,]*\\s*[-–~]\\s*[^\\d]{0,4}\\d");
    private static final int LARGO_CONSULTA = 200;
    private static final int LARGO_DESCRIPCION = 500;

    private final LectorRemotoClient cliente;
    private final Clock reloj;

    @Autowired
    public AlibabaLector(LectorRemotoClient cliente) {
        this(cliente, Clock.systemUTC());
    }

    AlibabaLector(LectorRemotoClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.ALIBABA);
    }

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        if (codigo != CodigoFuente.ALIBABA) {
            return ResultadoLectura.error("Este lector solo atiende Alibaba.", false);
        }
        String texto = textoDeBusqueda(consulta);
        if (texto == null) {
            return ResultadoLectura.sinResultados();
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.BUSQUEDA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaBuscar(reglas)) {
            return ResultadoLectura.error("Alibaba no tiene reglas de búsqueda completas: faltan ITEM, NOMBRE,"
                    + " PRECIO o ENLACE en Configuración > Fuentes.", false);
        }
        // Sin esto, una búsqueda sin resultados se tomaría como reglas rotas (CON_ERROR y aviso al Jefe).
        reglas.put("sinResultados", SIN_RESULTADOS);
        prepararPrecio(reglas);
        String url = urlBusqueda(urlBase, texto);
        return leer(sesion, s -> cliente.buscar(
                new ContratoLector.Peticion(CodigoFuente.ALIBABA.name(), url, reglas, s, null, MAX_RESULTADOS)));
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        if (codigo != CodigoFuente.ALIBABA) {
            return ResultadoLectura.error("Este lector solo atiende Alibaba.", false);
        }
        if (url == null || url.isBlank()) {
            return ResultadoLectura.error("Falta la URL de la ficha de Alibaba.", false);
        }
        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.FICHA,
                MONEDA, SEPARADOR_DECIMAL);
        if (!ReglasParaLector.sirveParaFicha(reglas)) {
            return ResultadoLectura.error("Alibaba no tiene reglas de ficha completas: faltan NOMBRE o PRECIO"
                    + " en Configuración > Fuentes.", false);
        }
        prepararPrecio(reglas);
        String limpia = url.trim();
        return leer(sesion, s -> cliente.leerFicha(
                new ContratoLector.Peticion(CodigoFuente.ALIBABA.name(), limpia, reglas, s, null, 1)));
    }

    /**
     * Rango de precio: el servicio de lectura toma el menor y la descripción trae el precio tal como
     * lo muestra Alibaba (el rango), que luego se anota en la ficha. También el aviso de captcha.
     */
    static void prepararPrecio(Map<String, Object> reglas) {
        reglas.put("precioRango", "menor");
        reglas.put("descripcion", reglas.get("precio"));
        reglas.put("verificacion", VERIFICACION);
    }

    /** Llama al servicio de lectura con la sesión y traduce la respuesta o el error. */
    private ResultadoLectura leer(String sesion, Function<ContratoLector.Sesion, ContratoLector.Respuesta> llamada) {
        ContratoLector.Sesion s;
        try {
            s = PuenteLector.sesion(sesion, reloj);
        } catch (IllegalArgumentException e) {
            return ResultadoLectura.error("La sesión guardada de Alibaba no se puede usar; vuelve a probar la sesión.",
                    false);
        }
        ResultadoLectura r;
        try {
            r = PuenteLector.aResultado(llamada.apply(s));
        } catch (LectorRemotoException e) {
            return PuenteLector.deError(TIENDA, e);
        }
        if (r.tipo() != ResultadoLectura.Tipo.OK) {
            return r;
        }
        List<FichaFuente> fichas = new ArrayList<>();
        for (FichaFuente f : r.fichas()) {
            fichas.add(new FichaFuente(f.nombre(), descripcionConRango(f.descripcion()), f.precio(), f.moneda(),
                    f.imagen(), f.enlace()));
        }
        return ResultadoLectura.ok(fichas);
    }

    /**
     * La descripción trae el precio tal como lo muestra Alibaba. Si es un rango, queda anotado
     * ("Precio en Alibaba por rango: US$12.50-15.00"); si es un solo precio, no aporta nada y se quita.
     */
    static String descripcionConRango(String textoPrecio) {
        if (textoPrecio == null) {
            return null;
        }
        String limpio = textoPrecio.replaceAll("\\s+", " ").trim();
        if (limpio.isEmpty() || !RANGO.matcher(limpio).find()) {
            return null;
        }
        String descripcion = PREFIJO_RANGO + limpio + " (se toma el menor)";
        return descripcion.length() <= LARGO_DESCRIPCION ? descripcion : descripcion.substring(0, LARGO_DESCRIPCION);
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

    /** https://www.alibaba.com/trade/search?SearchText=CAT+1R-0750 (la base sale de fuente_busqueda.url_base). */
    static String urlBusqueda(String urlBase, String texto) {
        String base = urlBase == null || urlBase.isBlank() ? URL_BASE : urlBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/trade/search?SearchText=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private static String unir(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return (x + " " + y).trim();
    }
}
