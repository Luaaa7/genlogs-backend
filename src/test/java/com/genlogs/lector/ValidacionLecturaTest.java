package com.genlogs.lector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.genlogs.app.sourcing.lector.ContratoLector.Credenciales;
import com.genlogs.app.sourcing.lector.ContratoLector.Estado;
import com.genlogs.app.sourcing.lector.ContratoLector.Peticion;
import com.genlogs.app.sourcing.lector.LectorRemotoException;

/** D-42 no cambia el ingreso de una sola pantalla: continuar es opcional. */
class ValidacionLecturaTest {

    private static Peticion sesion(Map<String, Object> login) {
        return new Peticion("EBAY", "https://www.ebay.com/login", Map.of("login", login),
                null, new Credenciales("persona", "clave-de-prueba"), null);
    }

    @Test
    void unPasoSigueSiendoValido() {
        assertDoesNotThrow(() -> ValidacionLectura.pedido("sesion", sesion(Map.of(
                "usuario", "#u", "clave", "#p", "enviar", "button", "exito", "#ok"))));
    }

    @Test
    void dosPasosAceptaContinuar() {
        assertDoesNotThrow(() -> ValidacionLectura.pedido("sesion", sesion(Map.of(
                "usuario", "#u", "continuar", "#next", "clave", "#p", "enviar", "button", "exito", "#ok"))));
    }

    @Test
    void unSelectorAjenoSigueSiendoInvalido() {
        LectorRemotoException ex = assertThrows(LectorRemotoException.class, () -> ValidacionLectura.pedido("sesion",
                sesion(Map.of("usuario", "#u", "clave", "#p", "enviar", "button", "exito", "#ok", "otro", "#x"))));
        assertEquals(Estado.SOLICITUD_INVALIDA, ex.getEstado());
    }
}
