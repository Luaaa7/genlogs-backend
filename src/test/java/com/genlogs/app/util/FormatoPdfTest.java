package com.genlogs.app.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class FormatoPdfTest {

    @Test
    void cantidad_quitaCerosSobrantes() {
        assertEquals("1", FormatoPdf.cantidad(new BigDecimal("1.000")));
        assertEquals("4", FormatoPdf.cantidad(new BigDecimal("4.000")));
        assertEquals("18", FormatoPdf.cantidad(new BigDecimal("18.000")));
        assertEquals("2.5", FormatoPdf.cantidad(new BigDecimal("2.500")));
        assertEquals("0.125", FormatoPdf.cantidad(new BigDecimal("0.125")));
        assertEquals("1,200", FormatoPdf.cantidad(new BigDecimal("1200.000")));
        assertEquals("0", FormatoPdf.cantidad(null));
    }

    @Test
    void importe_dosDecimalesYMiles() {
        assertEquals("4,209.30", FormatoPdf.importe(new BigDecimal("4209.30")));
        assertEquals("13,018.00", FormatoPdf.importe(new BigDecimal("13018")));
        assertEquals("0.00", FormatoPdf.importe(null));
        assertEquals("1,000,000.50", FormatoPdf.importe(new BigDecimal("1000000.5")));
        assertEquals("10.01", FormatoPdf.importe(new BigDecimal("10.005")));
    }

    @Test
    void monto_usaCodigoIso() {
        assertEquals("USD 13,018.00", FormatoPdf.monto("USD", new BigDecimal("13018.00")));
        assertEquals("PEN 4,209.30", FormatoPdf.monto("pen", new BigDecimal("4209.3")));
        assertEquals("PEN 0.00", FormatoPdf.monto(null, null));
    }
}
