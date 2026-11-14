package com.genlogs.app.sourcing.extraccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** A4: el precio y la moneda salen iguales venga de la tienda que venga. */
class NormalizadorPrecioTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "US $1,234.50        | 1234.50 | USD",
            "US$12.99            | 12.99   | USD",
            "$45                 | 45.00   | USD",
            "USD 1,234,567.8     | 1234567.80 | USD",
            "S/ 99.90            | 99.90   | PEN",
            "S/. 1,250.00        | 1250.00 | PEN",
            "12,50 €             | 12.50   | EUR",
            "EUR 1.234,50        | 1234.50 | EUR",
            "1 234,50 €          | 1234.50 | EUR",
            "£ 8.5               | 8.50    | GBP",
            "CN¥ 120.00          | 120.00  | CNY"
    })
    void normalizaPrecioYMonedaDelTexto(String texto, String monto, String moneda) {
        PrecioExtraido p = NormalizadorPrecio.normalizar(texto);
        assertEquals(new BigDecimal(monto), p.monto());
        assertEquals(moneda, p.codigoMoneda());
    }

    @Test
    void unRangoTomaElPrimerPrecio() {
        PrecioExtraido p = NormalizadorPrecio.normalizar("US $12.50 to US $15.00");
        assertEquals(new BigDecimal("12.50"), p.monto());
        assertEquals("USD", p.codigoMoneda());
    }

    @Test
    void tresDigitosDespuesDelSeparadorSonMiles() {
        assertEquals(new BigDecimal("1234.00"), NormalizadorPrecio.monto("1,234"));
        assertEquals(new BigDecimal("1234567.00"), NormalizadorPrecio.monto("1.234.567"));
        assertEquals(new BigDecimal("12.30"), NormalizadorPrecio.monto("12.3"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Consultar precio", "Gratis", "$0.00", "0", "S/ 0,00"})
    void sinPrecioQuedaVacioNuncaCero(String texto) {
        PrecioExtraido p = NormalizadorPrecio.normalizar(texto, "USD", "USD");
        assertNull(p.monto());
        assertNull(p.codigoMoneda());
        assertFalse(p.tienePrecio());
    }

    @Test
    void elTextoDeMonedaManda() {
        PrecioExtraido p = NormalizadorPrecio.normalizar("1,234.50", "S/", "USD");
        assertEquals(new BigDecimal("1234.50"), p.monto());
        assertEquals("PEN", p.codigoMoneda());
    }

    @Test
    void sinMonedaEnElTextoUsaLaDeLaFuente() {
        PrecioExtraido p = NormalizadorPrecio.normalizar("89.99", null, "usd");
        assertTrue(p.tienePrecio());
        assertEquals("USD", p.codigoMoneda());
    }

    @Test
    void sinMonedaNiPorDefectoLaMonedaQuedaVacia() {
        PrecioExtraido p = NormalizadorPrecio.normalizar("89.99");
        assertEquals(new BigDecimal("89.99"), p.monto());
        assertNull(p.codigoMoneda());
    }
}