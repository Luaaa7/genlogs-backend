package com.genlogs.lector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.genlogs.app.sourcing.lector.LectorRemotoException;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SeguridadLecturaTest {
    @Test void rechazaDestinosPrivadosReservadosMetadataEIpv6() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "0.0.0.0", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "198.18.0.1", "224.0.0.1", "240.0.0.1",
                "::1", "::", "fc00::1", "fe80::1", "ff02::1", "::ffff:127.0.0.1", "2001:db8::1"}) {
            assertFalse(ProxySalidaSegura.esPublica(InetAddress.getByName(ip)), ip);
        }
        assertTrue(ProxySalidaSegura.esPublica(InetAddress.getByName("8.8.8.8")));
        assertTrue(ProxySalidaSegura.esPublica(InetAddress.getByName("2606:4700:4700::1111")));
    }
    @Test void urlDeFuenteNoAdmiteSuplantacionNiProtocolosAlternativos() {
        for (String value : new String[]{"https://ebay.com.evil.test", "https://fakeebay.com", "http://www.ebay.com",
                "https://user:secret@ebay.com/", "https://ebay.com:8443", "file:///etc/passwd", "https://127.0.0.1/"}) {
            assertEquals(Estado.URL_NO_PERMITIDA, assertThrows(LectorRemotoException.class, () -> ValidacionLectura.url(value, "EBAY")).getEstado());
        }
        assertEquals("www.ebay.com", ValidacionLectura.url("https://www.ebay.com/itm/1", "EBAY").getHost());
    }
    @Test void proxyCompruebaTodasLasDireccionesDns() throws Exception {
        try (ProxySalidaSegura proxy = new ProxySalidaSegura(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")})) {
            try (Socket socket = new Socket("127.0.0.1", URI.create(proxy.url()).getPort())) {
                socket.setSoTimeout(2000);
                socket.getOutputStream().write("CONNECT www.ebay.com:443 HTTP/1.1\r\nHost: www.ebay.com:443\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                assertTrue(new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).startsWith("HTTP/1.1 403"));
            }
        }
    }
    @Test void sesionVencidaODeOtraFuenteNoSeUsa() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var empty = json.readTree("{\"cookies\":[],\"origins\":[]}");
        assertEquals(Estado.SESION_VENCIDA, assertThrows(LectorRemotoException.class,
                () -> ValidacionLectura.validarSesion(new Sesion("2000-01-01T00:00:00Z", empty), "EBAY")).getEstado());
        var other = json.readTree("{\"cookies\":[{\"domain\":\".amazon.com\"}],\"origins\":[]}");
        assertThrows(LectorRemotoException.class, () -> ValidacionLectura.validarSesion(new Sesion("2099-01-01T00:00:00Z", other), "EBAY"));
    }
}
