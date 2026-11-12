package com.genlogs.app.fuentes;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.fuentes.ClienteHttp.RespuestaHttp;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.FuenteBusqueda;
import com.genlogs.app.sourcing.ResultadoBusqueda;

/**
 * Lo común de una fuente con token de aplicación: cuota diaria, token en memoria,
 * un reintento si el token venció (401), pausa del día ante 429 o credenciales
 * rechazadas, y registro en el log de cada respuesta. Nunca escribe credenciales ni tokens en el log.
 */
public abstract class FuenteConToken implements FuenteBusqueda {

    /** Cuánto del cuerpo de una respuesta de error se escribe en el log. */
    static final int MAX_LOG = 300;

    protected final Logger log = LoggerFactory.getLogger(getClass());
    /** Precios como BigDecimal exacto, no double. */
    protected final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    protected final ClienteHttp http;
    protected final int maxResultados;

    private final ControlCuota cuota;
    private final TokenAplicacion token;

    protected FuenteConToken(ClienteHttp http, int limiteDiario, int maxResultados, Clock reloj) {
        this.http = http;
        this.maxResultados = Math.max(1, Math.min(maxResultados, 50));
        this.cuota = new ControlCuota(codigo(), limiteDiario, reloj);
        this.token = new TokenAplicacion(this::emitirToken, reloj);
    }

    /** Pide un token nuevo a la plataforma. */
    protected abstract HttpRequest peticionToken();

    /** Arma la búsqueda con el texto ya codificado para URL. */
    protected abstract HttpRequest peticionBusqueda(String textoUrl, String tokenAcceso);

    /** Convierte la respuesta de la plataforma en referencias. */
    protected abstract List<ResultadoBusqueda> leerResultados(JsonNode cuerpo);

    @Override
    public List<ResultadoBusqueda> buscar(ConsultaBusqueda consulta) {
        String texto = textoDeBusqueda(consulta);
        if (texto.isEmpty()) {
            return List.of();
        }
        cuota.reservar();
        String textoUrl = URLEncoder.encode(texto, StandardCharsets.UTF_8);
        long inicio = System.nanoTime();

        RespuestaHttp respuesta = http.enviar(peticionBusqueda(textoUrl, token.obtener()));
        if (respuesta.estado() == 401) {
            log.info("{}: token rechazado (401), se pide uno nuevo y se reintenta una vez", codigo());
            token.invalidar();
            respuesta = http.enviar(peticionBusqueda(textoUrl, token.obtener()));
        }
        if (!respuesta.exitosa()) {
            throw fallo("búsqueda", respuesta);
        }

        List<ResultadoBusqueda> resultados = leerResultados(leerJson(respuesta.cuerpo()));
        List<ResultadoBusqueda> recortados = resultados.size() > maxResultados
                ? resultados.subList(0, maxResultados) : resultados;
        log.info("{} respondió {} con {} resultado(s) para \"{}\" en {} ms (consultas hoy: {})",
                codigo(), respuesta.estado(), recortados.size(), texto,
                (System.nanoTime() - inicio) / 1_000_000, cuota.usadasHoy());
        return recortados;
    }

    /** Marca y N.º de parte; si no hay N.º de parte, el GTIN o las palabras de apoyo. */
    static String textoDeBusqueda(ConsultaBusqueda consulta) {
        String principal = tieneTexto(consulta.numeroParte()) ? consulta.numeroParte()
                : tieneTexto(consulta.gtin()) ? consulta.gtin()
                : consulta.palabras();
        String texto = Stream.of(consulta.marca(), principal)
                .filter(FuenteConToken::tieneTexto)
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return texto.length() > 200 ? texto.substring(0, 200) : texto;
    }

    private TokenAplicacion.Emitido emitirToken() {
        RespuestaHttp respuesta = http.enviar(peticionToken());
        if (!respuesta.exitosa()) {
            if (respuesta.estado() == 400 || respuesta.estado() == 401) {
                // Credenciales mal configuradas: no gastar la cuota de tokens reintentando todo el día.
                cuota.pausarHastaManana("credenciales rechazadas al pedir el token (" + respuesta.estado() + ")");
            }
            throw fallo("token", respuesta);
        }
        JsonNode cuerpo = leerJson(respuesta.cuerpo());
        String valor = cuerpo.path("access_token").asText("");
        if (valor.isBlank()) {
            throw new FuenteNoDisponibleException(codigo() + ": la respuesta del token no trae access_token");
        }
        log.info("{}: token de aplicación nuevo, vigente {} s", codigo(), cuerpo.path("expires_in").asLong(0));
        return new TokenAplicacion.Emitido(valor, cuerpo.path("expires_in").asLong(3600));
    }

    private FuenteNoDisponibleException fallo(String paso, RespuestaHttp respuesta) {
        String motivo;
        if (respuesta.estado() == 429) {
            motivo = "la plataforma limitó las consultas (429)";
            cuota.pausarHastaManana(motivo);
        } else {
            motivo = "respuesta " + respuesta.estado();
        }
        log.warn("{}: falló el paso de {} con {}: {}", codigo(), paso, respuesta.estado(), recortar(respuesta.cuerpo()));
        return new FuenteNoDisponibleException(codigo() + " (" + paso + "): " + motivo);
    }

    private JsonNode leerJson(String cuerpo) {
        try {
            return json.readTree(cuerpo == null ? "" : cuerpo);
        } catch (Exception e) {
            log.warn("{}: respuesta que no es JSON: {}", codigo(), recortar(cuerpo));
            throw new FuenteNoDisponibleException(codigo() + ": respuesta ilegible de la plataforma");
        }
    }

    protected static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Texto o null si el nodo falta o está vacío. */
    protected static String texto(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
            return null;
        }
        String valor = nodo.asText("").trim();
        return valor.isEmpty() ? null : valor;
    }

    private static String recortar(String cuerpo) {
        if (cuerpo == null) {
            return "";
        }
        String limpio = cuerpo.replaceAll("\\s+", " ");
        return limpio.length() > MAX_LOG ? limpio.substring(0, MAX_LOG) + "…" : limpio;
    }
}
