package com.genlogs.app.fuentes;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.genlogs.app.sourcing.ResultadoBusqueda;

/**
 * eBay Browse API (item_summary/search) con token de aplicación (client credentials,
 * scope api_scope). Solo existe si EBAY_CLIENT_ID y EBAY_CLIENT_SECRET están configuradas;
 * sin ellas la aplicación arranca igual y la búsqueda sigue con la captura por URL.
 */
@Component
@Order(1)
@Conditional(CondicionesFuentes.EbayConfigurado.class)
public class EbayFuente extends FuenteConToken {

    static final String CODIGO = "EBAY";
    static final String SCOPE = "https://api.ebay.com/oauth/api_scope";
    private static final Duration ESPERA = Duration.ofSeconds(15);

    private final String base;
    private final String marketplace;
    private final String credencialBasic;

    @Autowired
    public EbayFuente(ClienteHttp http,
            @Value("${genlogs.fuentes.ebay.client-id}") String clientId,
            @Value("${genlogs.fuentes.ebay.client-secret}") String clientSecret,
            @Value("${genlogs.fuentes.ebay.entorno:PRODUCCION}") String entorno,
            @Value("${genlogs.fuentes.ebay.marketplace:EBAY_US}") String marketplace,
            @Value("${genlogs.fuentes.ebay.limite-diario:1000}") int limiteDiario,
            @Value("${genlogs.fuentes.ebay.max-resultados:10}") int maxResultados) {
        this(http, clientId, clientSecret, entorno, marketplace, limiteDiario, maxResultados, Clock.systemUTC());
    }

    EbayFuente(ClienteHttp http, String clientId, String clientSecret, String entorno, String marketplace,
            int limiteDiario, int maxResultados, Clock reloj) {
        super(http, limiteDiario, maxResultados, reloj);
        this.base = "SANDBOX".equalsIgnoreCase(entorno == null ? "" : entorno.trim())
                ? "https://api.sandbox.ebay.com" : "https://api.ebay.com";
        this.marketplace = marketplace == null || marketplace.isBlank() ? "EBAY_US" : marketplace.trim();
        this.credencialBasic = Base64.getEncoder().encodeToString(
                (clientId.trim() + ":" + clientSecret.trim()).getBytes(StandardCharsets.UTF_8));
        log.info("eBay configurado: {} · {} · límite {} consultas/día", this.base, this.marketplace, limiteDiario);
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    protected HttpRequest peticionToken() {
        String cuerpo = "grant_type=client_credentials&scope="
                + java.net.URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);
        return HttpRequest.newBuilder(URI.create(base + "/identity/v1/oauth2/token"))
                .timeout(ESPERA)
                .header("Authorization", "Basic " + credencialBasic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
    }

    @Override
    protected HttpRequest peticionBusqueda(String textoUrl, String tokenAcceso) {
        URI uri = URI.create(base + "/buy/browse/v1/item_summary/search?q=" + textoUrl + "&limit=" + maxResultados);
        return HttpRequest.newBuilder(uri)
                .timeout(ESPERA)
                .header("Authorization", "Bearer " + tokenAcceso)
                .header("X-EBAY-C-MARKETPLACE-ID", marketplace)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    @Override
    protected List<ResultadoBusqueda> leerResultados(JsonNode cuerpo) {
        List<ResultadoBusqueda> resultados = new ArrayList<>();
        for (JsonNode item : cuerpo.path("itemSummaries")) {
            String titulo = texto(item.path("title"));
            String url = texto(item.path("itemWebUrl"));
            if (titulo == null || url == null) {
                continue;
            }
            String imagen = texto(item.path("image").path("imageUrl"));
            if (imagen == null) {
                imagen = texto(item.path("thumbnailImages").path(0).path("imageUrl"));
            }
            resultados.add(new ResultadoBusqueda(
                    titulo,
                    null,
                    null,
                    decimal(texto(item.path("price").path("value"))),
                    texto(item.path("price").path("currency")),
                    url,
                    imagen));
        }
        return resultados;
    }

    private static BigDecimal decimal(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
