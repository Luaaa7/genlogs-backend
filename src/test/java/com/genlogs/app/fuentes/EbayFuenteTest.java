package com.genlogs.app.fuentes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.MetodoBusqueda;
import com.genlogs.app.sourcing.ResultadoBusqueda;

/** eBay simulado: token, búsqueda, cuota, errores y que no se filtren credenciales. */
class EbayFuenteTest {

    private static final String TOKEN = "/identity/v1/oauth2/token";
    private static final String BUSQUEDA = "/buy/browse/v1/item_summary/search";
    private static final String TOKEN_OK = "{\"access_token\":\"v^1.1#tok-A\",\"expires_in\":7200,\"token_type\":\"Application Access Token\"}";
    private static final String DOS_ITEMS = """
            {"total":2,"itemSummaries":[
              {"itemId":"v1|1","title":"Filtro CAT 1R-0750","price":{"value":"25.99","currency":"USD"},
               "itemWebUrl":"https://www.ebay.com/itm/1","image":{"imageUrl":"https://i.ebayimg.com/1.jpg"}},
              {"itemId":"v1|2","title":"Filtro 1R0750 compatible","price":{"value":"18.50","currency":"USD"},
               "itemWebUrl":"https://www.ebay.com/itm/2","thumbnailImages":[{"imageUrl":"https://i.ebayimg.com/2.jpg"}]},
              {"itemId":"v1|3","price":{"value":"1.00","currency":"USD"},"itemWebUrl":"https://www.ebay.com/itm/3"}
            ]}""";

    private HttpSimulado http;
    private RelojMovible reloj;

    @BeforeEach
    void preparar() {
        http = new HttpSimulado();
        reloj = new RelojMovible("2026-11-12T15:00:00Z"); // 10:00 en Lima
    }

    private EbayFuente fuente(int limiteDiario) {
        return new EbayFuente(http, "mi-app-id", "mi-secreto", "PRODUCCION", "EBAY_US", limiteDiario, 10, reloj);
    }

    @Test
    void buscaPorMarcaYNumeroDeParteYConvierteLosResultados() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, DOS_ITEMS);

        List<ResultadoBusqueda> resultados = fuente(100).buscar(consulta("CAT", "1R-0750"));

        assertEquals(2, resultados.size(), "el ítem sin título se descarta");
        ResultadoBusqueda primero = resultados.get(0);
        assertEquals("Filtro CAT 1R-0750", primero.titulo());
        assertEquals(new BigDecimal("25.99"), primero.precio());
        assertEquals("USD", primero.codigoMoneda());
        assertEquals("https://www.ebay.com/itm/1", primero.urlReferencia());
        assertEquals("https://i.ebayimg.com/1.jpg", primero.urlImagen());
        assertNull(primero.marca(), "SourcingService completa la marca con la de la consulta");
        assertEquals("https://i.ebayimg.com/2.jpg", resultados.get(1).urlImagen());

        HttpRequest busqueda = http.ultimaA(BUSQUEDA);
        assertTrue(busqueda.uri().toString().startsWith("https://api.ebay.com" + BUSQUEDA + "?q=CAT+1R-0750&limit=10"));
        assertEquals("Bearer v^1.1#tok-A", busqueda.headers().firstValue("Authorization").orElseThrow());
        assertEquals("EBAY_US", busqueda.headers().firstValue("X-EBAY-C-MARKETPLACE-ID").orElseThrow());
    }

    @Test
    void elTokenSePideConBasicYElScopeDeBrowse() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, DOS_ITEMS);

        fuente(100).buscar(consulta("CAT", "1R-0750"));

        HttpRequest token = http.ultimaA(TOKEN);
        assertEquals("POST", token.method());
        String esperado = "Basic " + Base64.getEncoder()
                .encodeToString("mi-app-id:mi-secreto".getBytes(StandardCharsets.UTF_8));
        assertEquals(esperado, token.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void elTokenSeReutilizaHastaPocoAntesDeVencer() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, DOS_ITEMS);
        EbayFuente ebay = fuente(100);

        ebay.buscar(consulta("CAT", "A"));
        ebay.buscar(consulta("CAT", "B"));
        assertEquals(1, http.llamadasA(TOKEN));

        reloj.avanzar(Duration.ofSeconds(7200).minus(TokenAplicacion.MARGEN));
        ebay.buscar(consulta("CAT", "C"));
        assertEquals(2, http.llamadasA(TOKEN));
    }

    @Test
    void conToken401SePideOtroYSeReintentaUnaVez() {
        http.responder(TOKEN, 200, TOKEN_OK)
                .responder(BUSQUEDA, 401, "{\"errors\":[{\"errorId\":1001}]}")
                .responder(BUSQUEDA, 200, DOS_ITEMS);

        List<ResultadoBusqueda> resultados = fuente(100).buscar(consulta("CAT", "1R-0750"));

        assertEquals(2, resultados.size());
        assertEquals(2, http.llamadasA(TOKEN));
        assertEquals(2, http.llamadasA(BUSQUEDA));
    }

    @Test
    void alLlegarALaCuotaNoSeConsultaYSeDiceElMotivo() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, DOS_ITEMS);
        EbayFuente ebay = fuente(2);
        ebay.buscar(consulta("CAT", "A"));
        ebay.buscar(consulta("CAT", "B"));

        FuenteNoDisponibleException error = assertThrows(FuenteNoDisponibleException.class,
                () -> ebay.buscar(consulta("CAT", "C")));

        assertTrue(error.getMessage().contains("cuota diaria agotada"));
        assertEquals(2, http.llamadasA(BUSQUEDA), "la tercera no llega a eBay");

        // Al día siguiente (hora de Lima) vuelve a consultar.
        reloj.avanzar(Duration.ofHours(15));
        assertEquals(2, ebay.buscar(consulta("CAT", "D")).size());
    }

    @Test
    void un429PausaLaFuenteElRestoDelDia() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 429, "{\"errors\":[]}");
        EbayFuente ebay = fuente(100);

        assertThrows(FuenteNoDisponibleException.class, () -> ebay.buscar(consulta("CAT", "A")));
        FuenteNoDisponibleException segunda = assertThrows(FuenteNoDisponibleException.class,
                () -> ebay.buscar(consulta("CAT", "B")));

        assertTrue(segunda.getMessage().contains("en pausa hasta mañana"));
        assertEquals(1, http.llamadasA(BUSQUEDA));
    }

    @Test
    void unErrorDeLaPlataformaNoPausaYNoLlevaCredenciales() {
        http.responder(TOKEN, 200, TOKEN_OK)
                .responder(BUSQUEDA, 500, "{\"errors\":[{\"message\":\"Internal\"}]}")
                .responder(BUSQUEDA, 200, DOS_ITEMS);
        EbayFuente ebay = fuente(100);

        FuenteNoDisponibleException error = assertThrows(FuenteNoDisponibleException.class,
                () -> ebay.buscar(consulta("CAT", "A")));

        assertFalse(error.getMessage().contains("mi-secreto"));
        assertFalse(error.getMessage().contains("tok-A"));
        assertEquals(2, ebay.buscar(consulta("CAT", "B")).size(), "un 500 no pausa la fuente");
    }

    @Test
    void un403EnLaBusquedaEsUnErrorComunYNoPausa() {
        http.responder(TOKEN, 200, TOKEN_OK)
                .responder(BUSQUEDA, 403, "{\"errors\":[{\"errorId\":1100,\"message\":\"Access denied\"}]}")
                .responder(BUSQUEDA, 200, DOS_ITEMS);
        EbayFuente ebay = fuente(100);

        FuenteNoDisponibleException error = assertThrows(FuenteNoDisponibleException.class,
                () -> ebay.buscar(consulta("CAT", "A")));

        assertTrue(error.getMessage().contains("respuesta 403"));
        assertEquals(2, ebay.buscar(consulta("CAT", "B")).size(), "la siguiente búsqueda sí consulta eBay");
    }

    @Test
    void credencialesRechazadasPausanSinGastarTokens() {
        http.responder(TOKEN, 401, "{\"error\":\"invalid_client\"}");
        EbayFuente ebay = fuente(100);

        assertThrows(FuenteNoDisponibleException.class, () -> ebay.buscar(consulta("CAT", "A")));
        assertThrows(FuenteNoDisponibleException.class, () -> ebay.buscar(consulta("CAT", "B")));

        assertEquals(1, http.llamadasA(TOKEN));
        assertEquals(0, http.llamadasA(BUSQUEDA));
    }

    @Test
    void unaRespuestaQueNoEsJsonEsUnFalloControlado() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, "<html>mantenimiento</html>");

        assertThrows(FuenteNoDisponibleException.class, () -> fuente(100).buscar(consulta("CAT", "A")));
    }

    @Test
    void sinTextoParaBuscarNoSeConsultaNiSeGastaCuota() {
        EbayFuente ebay = fuente(1);

        assertTrue(ebay.buscar(new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, " ", null, null, null, null)).isEmpty());
        assertTrue(http.peticiones.isEmpty());
    }

    @Test
    void elTextoSeCodificaParaLaUrl() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, "{\"itemSummaries\":[]}");

        fuente(100).buscar(consulta("Komatsu & Co", "600-211/123#A"));

        String uri = http.ultimaA(BUSQUEDA).uri().toString();
        assertTrue(uri.contains("q=Komatsu+%26+Co+600-211%2F123%23A&"), uri);
    }

    @Test
    void enSandboxUsaElHostDePruebas() {
        http.responder(TOKEN, 200, TOKEN_OK).responder(BUSQUEDA, 200, "{}");
        EbayFuente sandbox = new EbayFuente(http, "id", "secreto", "sandbox", "EBAY_US", 10, 5, reloj);

        assertTrue(sandbox.buscar(consulta("CAT", "A")).isEmpty());
        assertTrue(http.ultimaA(BUSQUEDA).uri().toString().startsWith("https://api.sandbox.ebay.com/"));
        assertTrue(http.ultimaA(BUSQUEDA).uri().toString().endsWith("&limit=5"));
    }

    @Test
    void elTextoUsaGtinOPalabrasSiNoHayNumeroDeParte() {
        assertEquals("CAT 0123456789012", FuenteConToken.textoDeBusqueda(
                new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, null, "CAT", "0123456789012", "filtro", null)));
        assertEquals("filtro de aire", FuenteConToken.textoDeBusqueda(
                new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "", null, null, " filtro de aire ", null)));
    }

    private static ConsultaBusqueda consulta(String marca, String numeroParte) {
        return new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, numeroParte, marca, null, null, null);
    }
}
