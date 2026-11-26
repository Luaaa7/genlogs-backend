package com.genlogs.lector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServicioLecturaTest {
    private static final String TOKEN = "test-internal-token-at-least-32-characters";
    private static final ObjectMapper JSON = new ObjectMapper().setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    private final HttpClient http = HttpClient.newHttpClient();
    private HttpRequest solicitud(ServicioLectura service, String path, String token, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.port() + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> post(ServicioLectura service, String path, String token, String body) throws Exception {
        return http.send(solicitud(service, path, token, body), HttpResponse.BodyHandlers.ofString());
    }
    @Test void todasLasRutasRequierenTokenYLosErroresNoFiltranCuerpos() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ServicioLectura service = new ServicioLectura(new InetSocketAddress("127.0.0.1", 0), TOKEN, 2000,
                (op, req) -> { calls.incrementAndGet(); return LectorChromium.estado(Estado.OK); })) {
            service.start();
            for (String path : List.of("salud", "sesion", "buscar", "ficha", "url")) {
                assertEquals(401, post(service, "/interno/" + path, "wrong", "{}").statusCode());
            }
            var malformed = post(service, "/interno/url", TOKEN, "{clave:secret}");
            assertEquals(400, malformed.statusCode()); assertFalse(malformed.body().contains("secret"));
            assertEquals(413, post(service, "/interno/url", TOKEN, "x".repeat(513 * 1024)).statusCode());
            assertEquals(0, calls.get());
            String body = JSON.writeValueAsString(PaginasPrueba.CASOS.get(1).peticion(false));
            assertEquals(200, post(service, "/interno/url", TOKEN, body).statusCode());
            assertEquals(1, calls.get());
        }
    }
    @Test void clienteJavaConsumeLosCuatroEndpointsDelServicio() throws Exception {
        java.util.Set<String> operations = new java.util.HashSet<>();
        try (ServicioLectura service = new ServicioLectura(new InetSocketAddress("127.0.0.1", 0), TOKEN, 2000,
                (op, req) -> { operations.add(op); return new Respuesta(Estado.OK, List.of(),
                        "sesion".equals(op) ? new Sesion("2099-01-01T00:00:00Z", JSON.createObjectNode()) : null); })) {
            service.start();
            var client = new LectorRemotoClient("http://127.0.0.1:" + service.port(), TOKEN, 4000);
            var request = PaginasPrueba.CASOS.get(1).peticion(true);
            client.buscar(request); client.leerFicha(request); client.leerUrl(request);
            client.iniciarSesion(new Peticion("EBAY", "https://www.ebay.com/login", java.util.Map.of("login",
                    java.util.Map.of("usuario", "#u", "clave", "#p", "enviar", "button", "exito", "#ok")),
                    null, new Credenciales("test", "secret"), null));
            assertEquals(java.util.Set.of("sesion", "buscar", "ficha", "url"), operations);
        }
    }
    @Test void tiempoMaximoCubreElMotorCompletoYNoExponeSuExcepcion() throws Exception {
        try (ServicioLectura service = new ServicioLectura(new InetSocketAddress("127.0.0.1", 0), TOKEN, 100,
                (op, req) -> { try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return LectorChromium.estado(Estado.OK); })) {
            service.start();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                var response = post(service, "/interno/url", TOKEN, JSON.writeValueAsString(PaginasPrueba.CASOS.get(1).peticion(false)));
                assertEquals(504, response.statusCode()); assertTrue(response.body().contains("TIEMPO_AGOTADO"));
            });
        }
    }
    @Test void excepcionesDelMotorNoFiltranCredenciales() throws Exception {
        try (ServicioLectura service = new ServicioLectura(new InetSocketAddress("127.0.0.1", 0), TOKEN, 2000,
                (op, req) -> { throw new IllegalStateException("password=never-print-me"); })) {
            service.start();
            var response = post(service, "/interno/url", TOKEN, JSON.writeValueAsString(PaginasPrueba.CASOS.get(1).peticion(false)));
            assertEquals(502, response.statusCode()); assertFalse(response.body().contains("never-print-me"));
        }
    }
    @Test void unaSegundaLecturaRecibeOcupadoSinAbrirOtroNavegador() throws Exception {
        var iniciada = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var llamadas = new AtomicInteger();
        try (var lector = new ServicioLectura(new InetSocketAddress("127.0.0.1", 0), TOKEN, 10000,
                (op, req) -> {
                    if (llamadas.incrementAndGet() == 1) {
                        iniciada.countDown();
                        try {
                            if (!liberar.await(8, TimeUnit.SECONDS)) return LectorChromium.estado(Estado.TIEMPO_AGOTADO);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return LectorChromium.estado(Estado.INTERRUMPIDO);
                        }
                    }
                    return LectorChromium.estado(Estado.OK);
                })) {
            lector.start();
            String body = JSON.writeValueAsString(PaginasPrueba.CASOS.get(1).peticion(false));
            var primera = http.sendAsync(solicitud(lector, "/interno/url", TOKEN, body), HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(iniciada.await(2, TimeUnit.SECONDS));
                var simultanea = post(lector, "/interno/url", TOKEN, body);
                assertEquals(503, simultanea.statusCode());
                assertEquals(Estado.OCUPADO, JSON.readValue(simultanea.body(), Respuesta.class).estado());
                assertEquals(1, llamadas.get());
            } finally {
                liberar.countDown();
            }
            assertEquals(200, primera.get(3, TimeUnit.SECONDS).statusCode());
            assertEquals(200, post(lector, "/interno/url", TOKEN, body).statusCode());
            assertEquals(2, llamadas.get());
        }
    }
    @Test void noArrancaSinTokenSeguro() {
        assertThrows(IllegalArgumentException.class, () -> new ServicioLectura(new InetSocketAddress(0), "short", 2000, null));
    }
}
