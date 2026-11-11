package com.genlogs.app.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lectura de la respuesta de Factiliza para el tipo de cambio del día (D-19).
 * GET {base}/tipocambio/info/dia?fecha=AAAA-MM-DD
 * Respuesta: {"success":true,"data":{"fecha":"2026-11-11","compra":"3.751","venta":"3.758"}}
 * (compra y venta pueden venir como texto o como número).
 */
class FactilizaTipoCambioClientTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 11);

    @Test
    void leeCompraYVentaQueVienenComoTexto() {
        String json = "{\"success\":true,\"data\":{\"fecha\":\"2026-11-11\",\"compra\":\"3.751\",\"venta\":\"3.758\"}}";
        Optional<FactilizaTipoCambioClient.TipoCambioDia> tc = FactilizaTipoCambioClient.leer(json);
        assertEquals(true, tc.isPresent());
        assertEquals(DIA, tc.get().fecha());
        assertEquals(new BigDecimal("3.751"), tc.get().compra());
        assertEquals(new BigDecimal("3.758"), tc.get().venta());
    }

    @Test
    void leeVentaQueVieneComoNumero() {
        String json = "{\"success\":true,\"data\":{\"fecha\":\"2026-11-11\",\"compra\":3.751,\"venta\":3.758}}";
        assertEquals(new BigDecimal("3.758"), FactilizaTipoCambioClient.leer(json).get().venta());
    }

    @Test
    void sinCompra_igualDevuelveLaVenta() {
        String json = "{\"success\":true,\"data\":{\"fecha\":\"2026-11-11\",\"venta\":\"3.758\"}}";
        FactilizaTipoCambioClient.TipoCambioDia tc = FactilizaTipoCambioClient.leer(json).get();
        assertEquals(new BigDecimal("3.758"), tc.venta());
        assertEquals(null, tc.compra());
    }

    @Test
    void successFalse_noDevuelveNada() {
        String json = "{\"success\":false,\"message\":\"Sin datos para la fecha\"}";
        assertEquals(Optional.empty(), FactilizaTipoCambioClient.leer(json));
    }

    @Test
    void sinVenta_noDevuelveNada() {
        String json = "{\"success\":true,\"data\":{\"fecha\":\"2026-11-11\",\"compra\":\"3.751\"}}";
        assertEquals(Optional.empty(), FactilizaTipoCambioClient.leer(json));
    }

    @Test
    void ventaCeroONegativa_noDevuelveNada() {
        String json = "{\"success\":true,\"data\":{\"fecha\":\"2026-11-11\",\"venta\":\"0\"}}";
        assertEquals(Optional.empty(), FactilizaTipoCambioClient.leer(json));
    }

    @Test
    void ventaQueNoEsNumero_noDevuelveNada() {
        String json = "{\"success\":true,\"data\":{\"fecha\":\"2026-11-11\",\"venta\":\"N/D\"}}";
        assertEquals(Optional.empty(), FactilizaTipoCambioClient.leer(json));
    }

    @Test
    void jsonInvalido_noDevuelveNada() {
        assertEquals(Optional.empty(), FactilizaTipoCambioClient.leer("<html>error</html>"));
    }

    @Test
    void sinApiKey_noLlamaAFactilizaYNoDevuelveNada() {
        FactilizaTipoCambioClient client = new FactilizaTipoCambioClient("", "http://no-debe-llamarse.invalid");
        assertEquals(Optional.empty(), client.consultar(DIA));
    }
}
