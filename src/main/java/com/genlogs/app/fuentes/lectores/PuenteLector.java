package com.genlogs.app.fuentes.lectores;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.fuentes.config.FichaFuente;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoException;

/**
 * ME (v3.3) · Traduce entre el despachador (9-B.1) y el servicio de lectura (P1/P2).
 * Sirve para los 5 lectores: ninguno pide sesión ni marca estados, solo traduce.
 */
public final class PuenteLector {

    /** Si la sesión guardada no trae su vencimiento, se manda con este margen (SesionFuenteService ya la validó). */
    static final Duration VIGENCIA_SIN_FECHA = Duration.ofMinutes(30);
    private static final int LARGO_CONSULTA = 200;

    private static final ObjectMapper JSON = new ObjectMapper();

    private PuenteLector() {
    }

    /**
     * La sesión que manda el despachador, en el formato del servicio de lectura.
     * Acepta {"validaHasta": "...", "storageState": {...}} o el storageState solo
     * ({"cookies": [...], "origins": [...]}). Vacía → null (se lee sin sesión).
     *
     * @throws IllegalArgumentException si no es ninguno de los dos formatos
     */
    public static ContratoLector.Sesion sesion(String sesion, Clock reloj) {
        if (sesion == null || sesion.isBlank()) {
            return null;
        }
        JsonNode nodo;
        try {
            nodo = JSON.readTree(sesion);
        } catch (Exception e) {
            throw new IllegalArgumentException("La sesión no es JSON.");
        }
        if (nodo == null || !nodo.isObject()) {
            throw new IllegalArgumentException("La sesión no es un objeto JSON.");
        }
        String porDefecto = Instant.now(reloj).plus(VIGENCIA_SIN_FECHA).toString();
        if (nodo.has("storageState")) {
            return new ContratoLector.Sesion(instante(nodo.path("validaHasta").asText(null), porDefecto),
                    nodo.get("storageState"));
        }
        if (nodo.has("cookies")) {
            return new ContratoLector.Sesion(porDefecto, nodo);
        }
        throw new IllegalArgumentException("La sesión no trae cookies.");
    }

    /** Las fichas que leyó el servicio de lectura. Sin fichas → SIN_RESULTADOS. */
    public static ResultadoLectura aResultado(ContratoLector.Respuesta respuesta) {
        if (respuesta == null || respuesta.resultados() == null) {
            return ResultadoLectura.sinResultados();
        }
        List<FichaFuente> fichas = new ArrayList<>();
        for (ContratoLector.Resultado r : respuesta.resultados()) {
            if (r != null) {
                fichas.add(aFicha(r));
            }
        }
        return ResultadoLectura.ok(fichas);
    }

    /** El precio va en texto plano ("1234.50") y la moneda en ISO, como los espera NormalizadorFicha. */
    static FichaFuente aFicha(ContratoLector.Resultado r) {
        return new FichaFuente(r.nombre(), r.descripcion(), texto(r.precio()), r.codigoMoneda(),
                r.imagen(), r.enlace());
    }

    /**
     * Lo que respondió mal el servicio de lectura, en el tipo que espera el despachador.
     * El mensaje de LectorRemotoException ya es seguro (sin URLs, tokens ni cookies).
     */
    public static ResultadoLectura deError(String tienda, LectorRemotoException e) {
        String mensaje = tienda + ": " + e.getMessage();
        ContratoLector.Estado estado = e.getEstado();
        if (estado == null) {
            return ResultadoLectura.error(mensaje, false);
        }
        return switch (estado) {
            case VERIFICACION -> ResultadoLectura.verificacion(mensaje);
            case BLOQUEO -> ResultadoLectura.bloqueada(mensaje);
            case NO_CONFIGURADO -> ResultadoLectura.noConectado(mensaje);
            case OCUPADO -> ResultadoLectura.ocupado(mensaje);
            case TIEMPO_AGOTADO, SIN_RESPUESTA, INTERRUMPIDO -> ResultadoLectura.error(mensaje, true);
            default -> ResultadoLectura.error(mensaje, false);
        };
    }

    /**
     * Llama al servicio de lectura con la sesión y traduce la respuesta o el error.
     * El nombre de la tienda llega como argumento para que el mensaje diga Amazon, AliExpress o eBay.
     */
    public static ResultadoLectura leer(String tienda, String sesion, Clock reloj,
                                        Function<ContratoLector.Sesion, ContratoLector.Respuesta> llamada) {
        ContratoLector.Sesion s;
        try {
            s = sesion(sesion, reloj);
        } catch (IllegalArgumentException e) {
            return ResultadoLectura.error("La sesión guardada de " + tienda
                    + " no se puede usar; vuelve a probar la sesión.", false);
        }
        try {
            return aResultado(llamada.apply(s));
        } catch (LectorRemotoException e) {
            return deError(tienda, e);
        }
    }

    /** Qué se escribe en el buscador; null si no hay nada que buscar (o es búsqueda por imagen). */
    public static String textoDeBusqueda(ConsultaBusqueda consulta) {
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

    private static String unir(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return (x + " " + y).trim();
    }

    private static String instante(String texto, String porDefecto) {
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        try {
            return Instant.parse(texto.trim()).toString();
        } catch (DateTimeParseException e) {
            return porDefecto;
        }
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }
}
