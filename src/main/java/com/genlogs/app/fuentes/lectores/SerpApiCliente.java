package com.genlogs.app.fuentes.lectores;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FichaFuente;
import com.genlogs.app.fuentes.config.ResultadoLectura;

/** Consulta eBay y Amazon a traves de SerpApi. Solo se usa si existe SERPAPI_KEY. */
@Component
public class SerpApiCliente {

    private static final Logger log = LoggerFactory.getLogger(SerpApiCliente.class);

    static final String URL = "https://serpapi.com/search.json";
    static final String MONEDA = "USD";
    static final int MAX_RESULTADOS = 10;

    private final String clave;
    private final RestTemplate http;

    public SerpApiCliente(@Value("${genlogs.fuentes.serpapi.key:}") String clave) {
        this.clave = clave == null ? "" : clave.trim();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5000);
        fabrica.setReadTimeout(20000);
        this.http = new RestTemplate(fabrica);
    }

    public boolean configurado() {
        return !clave.isBlank();
    }

    public ResultadoLectura buscar(CodigoFuente codigo, String texto) {
        String motor;
        String parametro;
        if (codigo == CodigoFuente.EBAY) {
            motor = "ebay";
            parametro = "_nkw";
        } else if (codigo == CodigoFuente.AMAZON) {
            motor = "amazon";
            parametro = "k";
        } else {
            return ResultadoLectura.error("SerpApi solo atiende eBay y Amazon.", false);
        }
        if (texto == null || texto.isBlank()) {
            return ResultadoLectura.sinResultados();
        }
        URI uri = URI.create(URL + "?engine=" + motor
                + "&" + parametro + "=" + codificar(texto.trim())
                + "&api_key=" + codificar(clave));
        try {
            Map<?, ?> json = http.getForObject(uri, Map.class);
            if (json == null) {
                return ResultadoLectura.error("SerpApi no devolvio datos.", true);
            }
            Object error = json.get("error");
            if (error != null) {
                String mensaje = String.valueOf(error);
                if (mensaje.contains("returned any results")) {
                    return ResultadoLectura.sinResultados();
                }
                return ResultadoLectura.error("SerpApi: " + mensaje, false);
            }
            Object lista = json.get("organic_results");
            if (!(lista instanceof List<?> items)) {
                return ResultadoLectura.sinResultados();
            }
            List<FichaFuente> fichas = new ArrayList<>();
            for (Object o : items) {
                if (fichas.size() >= MAX_RESULTADOS) {
                    break;
                }
                if (!(o instanceof Map<?, ?> item)) {
                    continue;
                }
                String nombre = texto(item.get("title"));
                String enlace = texto(item.get("link"));
                if (nombre == null || enlace == null) {
                    continue;
                }
                fichas.add(new FichaFuente(nombre, texto(item.get("condition")), precio(item),
                        MONEDA, texto(item.get("thumbnail")), enlace));
            }
            return ResultadoLectura.ok(fichas);
        } catch (HttpStatusCodeException e) {
            int estado = e.getStatusCode().value();
            log.warn("SerpApi respondio {} para {}", estado, codigo);
            if (estado == 401) {
                return ResultadoLectura.error("SerpApi rechazo la clave: revisa SERPAPI_KEY en Render.", false);
            }
            if (estado == 429) {
                return ResultadoLectura.error("Se acabaron las busquedas del mes en SerpApi.", false);
            }
            return ResultadoLectura.error("SerpApi respondio con estado " + estado + ".", true);
        } catch (RestClientException e) {
            log.warn("SerpApi no responde para {}: {}", codigo, e.getMessage());
            return ResultadoLectura.error("SerpApi no responde; intenta de nuevo.", true);
        }
    }

    static String precio(Map<?, ?> item) {
        Object directo = item.get("extracted_price");
        if (directo instanceof Number n) {
            return numero(n);
        }
        Object precio = item.get("price");
        if (precio instanceof Map<?, ?> p) {
            if (p.get("extracted") instanceof Number n) {
                return numero(n);
            }
            if (p.get("from") instanceof Map<?, ?> desde && desde.get("extracted") instanceof Number n) {
                return numero(n);
            }
            return texto(p.get("raw"));
        }
        return texto(precio);
    }

    private static String numero(Number n) {
        return new BigDecimal(n.toString()).toPlainString();
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = String.valueOf(valor).trim();
        return s.isEmpty() ? null : s;
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}