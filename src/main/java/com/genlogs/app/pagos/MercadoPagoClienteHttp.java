package com.genlogs.app.pagos;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.exception.BusinessException;

/**
 * Cliente HTTP de Mercado Pago (Checkout Pro). Las claves salen de variables de entorno
 * en Render y nunca se escriben en el repositorio:
 * MERCADOPAGO_ACCESS_TOKEN y, opcional, MERCADOPAGO_NOTIFICATION_URL.
 */
@Component
public class MercadoPagoClienteHttp implements MercadoPagoCliente {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClienteHttp.class);
    private static final String API = "https://api.mercadopago.com";
    private static final String SIN_CONEXION = "No se pudo conectar con Mercado Pago. Inténtalo de nuevo en unos minutos.";

    private final String accessToken;
    private final String notificationUrl;
    private final String frontendUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public MercadoPagoClienteHttp(
            @Value("${MERCADOPAGO_ACCESS_TOKEN:}") String accessToken,
            @Value("${MERCADOPAGO_NOTIFICATION_URL:}") String notificationUrl,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.notificationUrl = notificationUrl == null ? "" : notificationUrl.trim();
        this.frontendUrl = primeraUrl(frontendUrl);
    }

    @Override
    public boolean habilitado() {
        return !accessToken.isEmpty();
    }

    @Override
    public Preferencia crearPreferencia(PedidoPreferencia pedido) {
        exigirHabilitado();
        String regreso = frontendUrl + "/portal/comprobantes/pago";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", pedido.referenciaExterna());
        item.put("title", pedido.titulo());
        item.put("quantity", 1);
        item.put("currency_id", pedido.moneda());
        item.put("unit_price", pedido.monto());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("external_reference", pedido.referenciaExterna());
        cuerpo.put("back_urls", Map.of("success", regreso, "pending", regreso, "failure", regreso));
        cuerpo.put("auto_return", "approved");
        cuerpo.put("statement_descriptor", "GENLOGS");
        if (!notificationUrl.isEmpty()) {
            cuerpo.put("notification_url", notificationUrl);
        }
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(API + "/checkout/preferences"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                log.warn("Mercado Pago rechazó la preferencia: HTTP {}", respuesta.statusCode());
                throw new BusinessException("Mercado Pago no aceptó el pago. Avísanos para revisarlo.");
            }
            JsonNode nodo = json.readTree(respuesta.body());
            String url = texto(nodo, "init_point");
            if (url == null) {
                url = texto(nodo, "sandbox_init_point");
            }
            if (url == null) {
                throw new BusinessException(SIN_CONEXION);
            }
            return new Preferencia(texto(nodo, "id"), url);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(SIN_CONEXION);
        } catch (Exception e) {
            log.warn("Error al crear la preferencia de Mercado Pago: {}", e.getMessage());
            throw new BusinessException(SIN_CONEXION);
        }
    }

    @Override
    public Pago consultarPago(String idPago) {
        exigirHabilitado();
        if (idPago == null || !idPago.matches("\\d{1,30}")) {
            throw new BusinessException("El número de pago no es válido.");
        }
        try {
            HttpRequest peticion = HttpRequest.newBuilder(
                            URI.create(API + "/v1/payments/" + URLEncoder.encode(idPago, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() == 404) {
                throw new BusinessException("Mercado Pago no encontró ese pago.");
            }
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                log.warn("Mercado Pago respondió HTTP {} al consultar el pago {}", respuesta.statusCode(), idPago);
                throw new BusinessException(SIN_CONEXION);
            }
            JsonNode nodo = json.readTree(respuesta.body());
            BigDecimal monto = nodo.hasNonNull("transaction_amount") ? nodo.get("transaction_amount").decimalValue() : null;
            return new Pago(texto(nodo, "id"), texto(nodo, "status"), texto(nodo, "external_reference"),
                    monto, texto(nodo, "currency_id"), fecha(texto(nodo, "date_approved")));
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(SIN_CONEXION);
        } catch (Exception e) {
            log.warn("Error al consultar el pago {} en Mercado Pago: {}", idPago, e.getMessage());
            throw new BusinessException(SIN_CONEXION);
        }
    }

    private void exigirHabilitado() {
        if (!habilitado()) {
            throw new BusinessException("El pago en línea todavía no está habilitado.");
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo == null ? null : nodo.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String s = valor.asText();
        return s.isBlank() ? null : s;
    }

    private static LocalDateTime fecha(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).toLocalDateTime();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** app.frontend-url puede traer varias URL separadas por coma: se usa la primera, sin "/" final. */
    static String primeraUrl(String valor) {
        String url = valor == null || valor.isBlank() ? "http://localhost:5173" : valor.split(",")[0].trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}