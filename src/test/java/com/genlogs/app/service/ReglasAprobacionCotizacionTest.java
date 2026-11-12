package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.exception.BusinessException;

class ReglasAprobacionCotizacionTest {

    @Test
    void borradorNoPasaDirectoAEnviada() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                ReglasAprobacionCotizacion.validarCambioLibre("BORRADOR", "ENVIADA"));
        assertEquals(ReglasAprobacionCotizacion.BORRADOR_NO_PASA_A_ENVIADA, error.getMessage());
    }

    @Test
    void soloElBorradorSeSolicitaYSoloLaPendienteSeApruebaODevuelve() {
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.exigir(
                "BORRADOR", CodigoEstadoCotizacion.BORRADOR, ReglasAprobacionCotizacion.SOLO_BORRADOR));
        assertThrows(BusinessException.class, () -> ReglasAprobacionCotizacion.exigir(
                "ENVIADA", CodigoEstadoCotizacion.BORRADOR, ReglasAprobacionCotizacion.SOLO_BORRADOR));
        assertThrows(BusinessException.class, () -> ReglasAprobacionCotizacion.exigir(
                "BORRADOR", CodigoEstadoCotizacion.PENDIENTE_APROBACION, ReglasAprobacionCotizacion.SOLO_PENDIENTE));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.exigir(
                "PENDIENTE_APROBACION",
                CodigoEstadoCotizacion.PENDIENTE_APROBACION,
                ReglasAprobacionCotizacion.SOLO_PENDIENTE));
    }

    @Test
    void enviarExigeAprobacionInterna() {
        assertThrows(BusinessException.class, () -> ReglasAprobacionCotizacion.exigir(
                "BORRADOR",
                CodigoEstadoCotizacion.APROBADA_INTERNA,
                ReglasAprobacionCotizacion.SOLO_APROBADA_INTERNA));
        assertThrows(BusinessException.class, () -> ReglasAprobacionCotizacion.exigir(
                "PENDIENTE_APROBACION",
                CodigoEstadoCotizacion.APROBADA_INTERNA,
                ReglasAprobacionCotizacion.SOLO_APROBADA_INTERNA));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.exigir(
                "APROBADA_INTERNA",
                CodigoEstadoCotizacion.APROBADA_INTERNA,
                ReglasAprobacionCotizacion.SOLO_APROBADA_INTERNA));
    }

    @Test
    void devolverExigeMotivo() {
        assertThrows(BusinessException.class, () -> ReglasAprobacionCotizacion.exigirMotivo("  "));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.exigirMotivo("Falta el plazo"));
    }

    @Test
    void losEstadosDelClienteSiguenEnCambioLibre() {
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.validarCambioLibre("ENVIADA", "EN_NEGOCIACION"));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.validarCambioLibre("EN_NEGOCIACION", "APROBADA"));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.validarCambioLibre("ENVIADA", "RECHAZADA"));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.validarCambioLibre("ENVIADA", "VENCIDA"));
        assertDoesNotThrow(() -> ReglasAprobacionCotizacion.validarCambioLibre("BORRADOR", "ANULADA"));
    }
}
