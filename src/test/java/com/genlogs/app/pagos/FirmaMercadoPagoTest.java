package com.genlogs.app.pagos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FirmaMercadoPagoTest {

    private static final String SECRETO = "clave-de-prueba";

    @Test
    void elManifiestoSigueElFormatoDeMercadoPago() {
        assertEquals("id:123456;request-id:req-1;ts:1700000000;",
                FirmaMercadoPago.manifiesto("123456", "req-1", "1700000000"));
        assertEquals("ts:1700000000;", FirmaMercadoPago.manifiesto(null, null, "1700000000"));
        assertEquals("id:abc;ts:1;", FirmaMercadoPago.manifiesto("ABC", "", "1"));
    }

    @Test
    void unaFirmaCalculadaConLaClaveEsValida() {
        String v1 = FirmaMercadoPago.hmacHex("id:123456;request-id:req-1;ts:1700000000;", SECRETO);
        assertTrue(FirmaMercadoPago.valida("ts=1700000000,v1=" + v1, "req-1", "123456", SECRETO));
        assertTrue(FirmaMercadoPago.valida(" ts=1700000000 , v1=" + v1.toUpperCase(), "req-1", "123456", SECRETO));
    }

    @Test
    void otraClaveOtroPagoOFirmaIncompletaNoSonValidas() {
        String v1 = FirmaMercadoPago.hmacHex("id:123456;request-id:req-1;ts:1700000000;", SECRETO);
        assertFalse(FirmaMercadoPago.valida("ts=1700000000,v1=" + v1, "req-1", "123456", "otra-clave"));
        assertFalse(FirmaMercadoPago.valida("ts=1700000000,v1=" + v1, "req-1", "999999", SECRETO));
        assertFalse(FirmaMercadoPago.valida("v1=" + v1, "req-1", "123456", SECRETO));
        assertFalse(FirmaMercadoPago.valida(null, "req-1", "123456", SECRETO));
        // Sin clave configurada nunca se acepta un aviso.
        assertFalse(FirmaMercadoPago.valida("ts=1700000000,v1=" + v1, "req-1", "123456", ""));
    }
}