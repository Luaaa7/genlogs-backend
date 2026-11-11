package com.genlogs.app.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consulta el tipo de cambio del día en Factiliza (D-19): valor SUNAT, se usa el de venta.
 * GET {factiliza.base-url}/tipocambio/info/dia?fecha=AAAA-MM-DD con el mismo token que el RUC.
 *
 * Nunca lanza error: si no hay token, Factiliza no responde o la respuesta no sirve,
 * devuelve vacío y quien lo llama aplica el respaldo (último valor o corrección manual
 * del Jefe Administrativo).
 */
@Component
public class FactilizaTipoCambioClient {

    private static final Logger log = LoggerFactory.getLogger(FactilizaTipoCambioClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record TipoCambioDia(LocalDate fecha, BigDecimal compra, BigDecimal venta) {}

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FactilizaTipoCambioClient(@Value("${factiliza.api-key:}") String apiKey,
                                     @Value("${factiliza.base-url:https://api.factiliza.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public Optional<TipoCambioDia> consultar(LocalDate fecha) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FACTILIZA_API_KEY no configurada: no se consulta el tipo de cambio del {}", fecha);
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tipocambio/info/dia?fecha=" + fecha))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Factiliza respondió {} al consultar el tipo de cambio del {}", response.statusCode(), fecha);
                return Optional.empty();
            }
            return leer(response.body())
                    .map(tc -> tc.fecha() != null ? tc : new TipoCambioDia(fecha, tc.compra(), tc.venta()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("No se pudo consultar el tipo de cambio del {} en Factiliza: {}", fecha, e.getMessage());
            return Optional.empty();
        }
    }

    /** Lee la respuesta de Factiliza; vacío si no trae un valor de venta mayor que cero. */
    static Optional<TipoCambioDia> leer(String json) {
        try {
            JsonNode raiz = MAPPER.readTree(json);
            if (!raiz.path("success").asBoolean(false)) {
                return Optional.empty();
            }
            JsonNode data = raiz.path("data");
            BigDecimal venta = numero(data.path("venta"));
            if (venta == null || venta.signum() <= 0) {
                return Optional.empty();
            }
            String fecha = data.path("fecha").asText("");
            return Optional.of(new TipoCambioDia(
                    fecha.isBlank() ? null : LocalDate.parse(fecha),
                    numero(data.path("compra")),
                    venta));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static BigDecimal numero(JsonNode nodo) {
        if (nodo.isMissingNode() || nodo.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(nodo.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
