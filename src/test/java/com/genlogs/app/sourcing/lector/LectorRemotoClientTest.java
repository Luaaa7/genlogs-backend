package com.genlogs.app.sourcing.lector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LectorRemotoClientTest {
    private static final String TOKEN = "internal-test-token-at-least-32-characters";
    private static final String OK = "{\"estado\":\"OK\",\"resultados\":[]}";
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = OK;
    private volatile boolean failFirst;
    private volatile long delay;

    @BeforeEach void start() throws Exception {
        executor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            int count = calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            int code = failFirst && count == 1 ? 503 : status;
            byte[] bytes = (code == 503 ? "{\"estado\":\"OCUPADO\",\"resultados\":[]}" : body).getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (status == 302) exchange.getResponseHeaders().add("Location", "/do-not-follow");
                exchange.sendResponseHeaders(code, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
    private LectorRemotoClient client(long timeout) {
        return new LectorRemotoClient("http://127.0.0.1:" + server.getAddress().getPort(), TOKEN, timeout);
    }
    private Peticion request() {
        return new Peticion("EBAY", "https://www.ebay.com/itm/1", Map.of(
                "nombre", Map.of("selector", "h1"), "precio", Map.of("selector", ".price"),
                "codigoMoneda", "USD"), null, null, 8);
    }

    @Test void enviaContratoTokenYNoIncluyeNulos() throws Exception {
        assertEquals(Estado.OK, client(3000).leerUrl(request()).estado());
        assertEquals("Bearer " + TOKEN, authorization.get());
        assertEquals("/interno/url", path.get());
        var parsed = new ObjectMapper().readTree(requestBody.get());
        assertFalse(parsed.has("credenciales"));
        assertFalse(parsed.has("sesion"));
        assertEquals("EBAY", parsed.path("fuente").asText());
    }
    @Test void reintentaUnaVezTrasFallaTransitoria() {
        failFirst = true;
        assertEquals(Estado.OK, client(3000).buscar(request()).estado());
        assertEquals(2, calls.get());
    }
    @Test void noReintentaBloqueoVerificacionSesionVencidaNiToken() {
        for (Estado expected : new Estado[]{Estado.BLOQUEO, Estado.VERIFICACION, Estado.SESION_VENCIDA}) {
            calls.set(0); body = "{\"estado\":\"" + expected + "\",\"resultados\":[]}";
            assertEquals(expected, assertThrows(LectorRemotoException.class, () -> client(3000).leerFicha(request())).getEstado());
            assertEquals(1, calls.get());
        }
        calls.set(0); status = 401;
        assertEquals(Estado.NO_AUTORIZADO, assertThrows(LectorRemotoException.class, () -> client(3000).buscar(request())).getEstado());
        assertEquals(1, calls.get());
    }
    @Test void noRepiteInicioDeSesionQuePuedeBloquearUnaCuenta() {
        status = 503;
        assertThrows(LectorRemotoException.class, () -> client(3000).iniciarSesion(request()));
        assertEquals(1, calls.get());
    }
    @Test void clasificaFallaPersistenteYLimitaLosIntentos() {
        status = 502; body = "gateway unavailable";
        assertEquals(Estado.SIN_RESPUESTA, assertThrows(LectorRemotoException.class, () -> client(3000).buscar(request())).getEstado());
        assertEquals(2, calls.get());
    }
    @Test void noSigueRedireccionesNiFiltraCuerposDelServidor() {
        status = 302; body = "secreto-que-no-debe-salir";
        var error = assertThrows(LectorRemotoException.class, () -> client(3000).buscar(request()));
        assertEquals(Estado.RESPUESTA_INVALIDA, error.getEstado());
        assertFalse(error.toString().contains(body));
        assertEquals(1, calls.get());
    }
    @Test void limiteTotalIncluyeElReintento() {
        delay = 1000;
        assertTimeoutPreemptively(Duration.ofMillis(1500), () -> {
            var error = assertThrows(LectorRemotoException.class, () -> client(300).leerUrl(request()));
            assertEquals(Estado.TIEMPO_AGOTADO, error.getEstado());
        });
        assertTrue(calls.get() <= 2);
    }
    @Test void respuestasMalformadasNoPasanComoExito() {
        for (String malformed : new String[]{"not json", "{}", "null", "{\"estado\":\"OK\"}", "{\"estado\":\"NUEVO\",\"resultados\":[]}"}) {
            body = malformed;
            assertEquals(Estado.RESPUESTA_INVALIDA, assertThrows(LectorRemotoException.class, () -> client(3000).leerUrl(request())).getEstado());
        }
    }
    @Test void credencialesYSesionNoAparecenEnToString() throws Exception {
        var credentials = new Credenciales("usuario-secreto", "clave-secreta");
        var session = new Sesion("2099-01-01T00:00:00Z", new ObjectMapper().readTree("{\"cookie\":\"cookie-secreta\"}"));
        var req = new Peticion("EBAY", "https://www.ebay.com/?secret", Map.of(), session, credentials, 8);
        assertFalse(credentials.toString().contains("clave-secreta"));
        assertFalse(session.toString().contains("cookie-secreta"));
        assertEquals("PeticionLector[REDACTADO]", req.toString());
    }
    @Test void clienteOpcionalNoHaceRedSiFaltaConfiguracion() {
        assertEquals(Estado.NO_CONFIGURADO, assertThrows(LectorRemotoException.class,
                () -> new LectorRemotoClient("", "", 1000).buscar(request())).getEstado());
        assertEquals(0, calls.get());
    }
    @Test void interrupcionConservaLaBandera() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(Estado.INTERRUMPIDO, assertThrows(LectorRemotoException.class, () -> client(3000).leerUrl(request())).getEstado());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
