package com.genlogs.app.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tipo de cambio de Factiliza (D-19). No consulta RUC ni DNI.
 * GET {base}/tipocambio/info/dia?fecha=YYYY-MM-DD y usa data.venta.
 */
@Component
public class ClienteFactilizaTipoCambio implements ConsultaTipoCambio {

    private static final Logger log = LoggerFactory.getLogger(ClienteFactilizaTipoCambio.class);

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ClienteFactilizaTipoCambio(
            @Value("${factiliza.api-key:}") String apiKey,
            @Value("${factiliza.base-url:https://api.factiliza.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
    }

    @Override
    public BigDecimal ventaDelDia(LocalDate fecha) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No hay llave de Factiliza");
        }
        if (fecha == null) {
            throw new IllegalStateException("Falta la fecha del tipo de cambio");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tipocambio/info/dia?fecha=" + fecha))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = cliente.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("Factiliza respondió {} al consultar el tipo de cambio", response.statusCode());
                throw new IllegalStateException("Factiliza respondió " + response.statusCode());
            }
            return venta(mapper.readTree(response.body()), fecha);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo consultar el tipo de cambio", e);
        }
    }

    private static BigDecimal venta(JsonNode raiz, LocalDate fecha) {
        int estado = raiz.path("status").asInt(200);
        if (estado >= 300 || raiz.path("success").asText("").equals("false")) {
            throw new IllegalStateException("Factiliza no devolvió el tipo de cambio");
        }
        JsonNode data = raiz.path("data");
        String fechaRespuesta = data.path("fecha").asText("");
        if (!fechaRespuesta.isBlank() && !fecha.equals(LocalDate.parse(fechaRespuesta))) {
            throw new IllegalStateException("Factiliza devolvió el tipo de cambio de " + fechaRespuesta);
        }
        JsonNode nodo = data.path("venta");
        if (nodo.isMissingNode() || nodo.isNull() || nodo.asText().isBlank()) {
            throw new IllegalStateException("Factiliza no trajo el valor de venta");
        }
        BigDecimal venta;
        try {
            venta = new BigDecimal(nodo.asText());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Factiliza trajo un valor de venta que no es un número");
        }
        if (venta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Factiliza trajo un valor de venta que no sirve");
        }
        return venta;
    }
}
