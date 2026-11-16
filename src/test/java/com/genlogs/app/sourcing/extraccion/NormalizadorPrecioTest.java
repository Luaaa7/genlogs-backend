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

/** A4: el precio y la moneda salen iguales venga de la tienda que venga (incluye la auditoría del PR #94). */
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

    @ParameterizedTest(name = "\"{0}\" -> {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "MX$ 1,200.00       | 1200.00 | MXN",
            "C$ 20              | 20.00   | CAD",
            "CA$ 20             | 20.00   | CAD",
            "AU$ 15.99          | 15.99   | AUD",
            "R$ 50,00           | 50.00   | BRL",
            "12.99 US dollars   | 12.99   | USD",
            "S/ 99 cada uno     | 99.00   | PEN"
    })
    void reconoceOtrosDolaresYPesosSinConfundirPalabras(String texto, String monto, String moneda) {
        PrecioExtraido p = NormalizadorPrecio.normalizar(texto);
        assertEquals(new BigDecimal(monto), p.monto());
        assertEquals(moneda, p.codigoMoneda());
    }

    @Test
    void unDolarSoloUsaLaMonedaDeLaFuente() {
        assertEquals("MXN", NormalizadorPrecio.normalizar("$1,200", null, "MXN").codigoMoneda());
        assertEquals("ARS", NormalizadorPrecio.normalizar("$ 35.000", null, "ARS").codigoMoneda());
        assertEquals("USD", NormalizadorPrecio.normalizar("$45", null, null).codigoMoneda());
        assertEquals("USD", NormalizadorPrecio.normalizar("US $45", null, "MXN").codigoMoneda());
    }

    // ---------------------------------------------------------------- auditoría PR #94

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "0.004   | 0.004",
            "0.005   | 0.005",
            "0,004   | 0.004",
            "$0.50   | 0.50",
            "1,234   | 1234.00",
            "12.3    | 12.30"
    })
    void conParteEnteraCeroLosTresDigitosSonDecimalesYNoSeRedondeaACero(String texto, String monto) {
        assertEquals(new BigDecimal(monto), NormalizadorPrecio.monto(texto));
    }

    @Test
    void unaMonedaEscritaQueNoSeReconoceNoSeCambiaPorLaDeLaFuente() {
        assertNull(NormalizadorPrecio.normalizar("50", "Bs.", "USD").codigoMoneda());
        assertNull(NormalizadorPrecio.normalizar("50", "XYZ", "USD").codigoMoneda());
        assertNull(NormalizadorPrecio.normalizar("₹ 500", null, "USD").codigoMoneda());
        assertNull(NormalizadorPrecio.normalizar("₩ 12,000", null, "USD").codigoMoneda());
        assertEquals(new BigDecimal("500.00"), NormalizadorPrecio.normalizar("₹ 500", null, "USD").monto());
    }

    @Test
    void unCodigoIsoValidoQueNoEstaEnElMapaSeRespeta() {
        assertEquals("JPY", NormalizadorPrecio.normalizar("50", "jpy", "USD").codigoMoneda());
        assertEquals("JPY", NormalizadorPrecio.normalizar("1,000 JPY", null, "USD").codigoMoneda());
        assertEquals("CHF", NormalizadorPrecio.normalizar("CHF 45.90", null, "USD").codigoMoneda());
        assertEquals("MXN", NormalizadorPrecio.normalizar("50", "$", "MXN").codigoMoneda());
    }

    @Test
    void laMonedaPorDefectoSoloSeUsaSiEsIsoValida() {
        assertNull(NormalizadorPrecio.normalizar("89.99", null, "ZZZ").codigoMoneda());
        assertNull(NormalizadorPrecio.normalizar("89.99", null, "US").codigoMoneda());
        assertNull(NormalizadorPrecio.normalizar("89.99", null, "DOLAR").codigoMoneda());
        assertNull(NormalizadorPrecio.normalizar("89.99", null, "XXX").codigoMoneda());
        assertEquals("USD", NormalizadorPrecio.normalizar("$45", null, "ZZZ").codigoMoneda());
        assertEquals("PEN", NormalizadorPrecio.normalizar("89.99", null, " pen ").codigoMoneda());
        assertNull(NormalizadorPrecio.iso("ABC"));
        assertEquals("EUR", NormalizadorPrecio.iso("eur"));
    }

    @ParameterizedTest(name = "espacio Unicode U+{0}")
    @ValueSource(strings = {"00A0", "202F", "2009", "2007"})
    void reconoceLosEspaciosUnicode(String codigo) {
        String espacio = String.valueOf((char) Integer.parseInt(codigo, 16));
        PrecioExtraido us = NormalizadorPrecio.normalizar("US" + espacio + "$45", null, "MXN");
        assertEquals(new BigDecimal("45.00"), us.monto());
        assertEquals("USD", us.codigoMoneda());
        PrecioExtraido eur = NormalizadorPrecio.normalizar("1" + espacio + "234,50" + espacio + "€");
        assertEquals(new BigDecimal("1234.50"), eur.monto());
        assertEquals("EUR", eur.codigoMoneda());
    }

    @Test
    void unPorcentajeNoEsPrecio() {
        PrecioExtraido p = NormalizadorPrecio.normalizar("20% off US $45.00");
        assertEquals(new BigDecimal("45.00"), p.monto());
        assertEquals("USD", p.codigoMoneda());
        assertEquals(new BigDecimal("10.00"), NormalizadorPrecio.monto("Ahorra 15 % · S/ 10"));
        assertNull(NormalizadorPrecio.monto("Ahorra 15 %"));
        assertNull(NormalizadorPrecio.monto("-20%"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-$5.00", "$-5", "- US $12", "\u22125,00 €", "S/ -99.90", "-12"})
    void unPrecioNegativoNoEsPrecio(String texto) {
        assertEquals(PrecioExtraido.VACIO, NormalizadorPrecio.normalizar(texto, null, "USD"));
    }

    @Test
    void unRangoConGuionSigueTomandoElPrimero() {
        assertEquals(new BigDecimal("12.50"), NormalizadorPrecio.monto("US $12.50 - US $15.00"));
        assertEquals(new BigDecimal("12.50"), NormalizadorPrecio.monto("$12.50-$15.00"));
    }
}