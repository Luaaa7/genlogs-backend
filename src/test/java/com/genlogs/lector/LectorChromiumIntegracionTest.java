package com.genlogs.lector;

import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import com.microsoft.playwright.Route.FulfillOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.net.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Ejecutar con -Dlector.integration=true tras instalar Chromium; sin conexiones a las tiendas. */
@EnabledIfSystemProperty(named = "lector.integration", matches = "true")
class LectorChromiumIntegracionTest {
    @Test void clienteServicioYChromiumExtraenLasCincoTiendas() throws Exception {
        var motor = new LectorChromium(10000, context -> context.route("**/*", route -> {
            URI url = URI.create(route.request().url());
            var caso = PaginasPrueba.CASOS.stream().filter(c -> c.host().equals(url.getHost())).findFirst().orElseThrow();
            route.fulfill(new FulfillOptions().setContentType("text/html")
                    .setBody(url.getPath().contains("buscar") ? caso.busqueda() : caso.ficha()));
        }));
        String token = "test-contract-token-at-least-32-characters";
        try (ServicioLectura service = new ServicioLectura(new InetSocketAddress("127.0.0.1", 0), token, 10000, motor)) {
            service.start();
            var client = new LectorRemotoClient("http://127.0.0.1:" + service.port(), token, 24000);
            for (var caso : PaginasPrueba.CASOS) {
                var rows = client.buscar(caso.peticion(true)).resultados();
                assertEquals(2, rows.size());
                assertEquals(new BigDecimal(caso.esperado()), rows.get(0).precio());
                assertEquals("Filtro de combustible de prueba", rows.get(0).descripcion());
                assertEquals(1, client.leerFicha(caso.peticion(false)).resultados().size());
                assertEquals(1, client.leerUrl(caso.peticion(false)).resultados().size());
            }
        }
    }
    private LectorChromium fixture(String html, int status) {
        return new LectorChromium(10000, context -> context.route("**/*", route ->
                route.fulfill(new FulfillOptions().setStatus(status).setContentType("text/html").setBody(html))));
    }
    @Test void detectaCaptchaCodigoBloqueoYSesionVencida() {
        var req = PaginasPrueba.CASOS.get(1).peticion(false);
        assertEquals(Estado.VERIFICACION, fixture("<input id=captcha>", 200).ejecutar("url", req).estado());
        assertEquals(Estado.VERIFICACION, fixture("<input autocomplete=one-time-code>", 200).ejecutar("url", req).estado());
        assertEquals(Estado.BLOQUEO, fixture("<p>Forbidden</p>", 403).ejecutar("url", req).estado());
        assertEquals(Estado.BLOQUEO, fixture("<p>Rate limited</p>", 429).ejecutar("url", req).estado());
        assertEquals(Estado.SESION_VENCIDA, fixture("<p>Login</p>", 401).ejecutar("url", req).estado());
    }
    @Test void iniciaSesionSinPersistirNiDevolverCredenciales() {
        String html = """
                <form onsubmit="event.preventDefault(); document.cookie='session=fixture; Secure; SameSite=Lax';
                document.body.innerHTML='<h1 id=ok>Sesión lista</h1>'">
                <input id=user><input id=pass type=password><button>Entrar</button></form>
                """;
        var req = new Peticion("EBAY", "https://www.ebay.com/login", Map.of("login", Map.of(
                "usuario", "#user", "clave", "#pass", "enviar", "button", "exito", "#ok")),
                null, new Credenciales("test-user", "test-secret"), null);
        var response = fixture(html, 200).ejecutar("sesion", req);
        assertEquals(Estado.OK, response.estado());
        assertNotNull(response.sesion());
        assertEquals("session", response.sesion().storageState().path("cookies").get(0).path("name").asText());
        assertFalse(response.sesion().storageState().toString().contains("test-secret"));
        assertEquals(Estado.URL_NO_PERMITIDA, fixture(html.replace("<form ", "<form action='https://evil.test/' "), 200).ejecutar("sesion", req).estado());
    }
}
