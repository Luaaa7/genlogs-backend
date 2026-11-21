package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.exception.BusinessException;

class ReglasAprobacionCotizacionTest {

    @Test
    void borradorNoPasaDirectoAEnviadaNiALosEstadosDeAprobacion() {
        for (String destino : new String[] {"ENVIADA", "PENDIENTE_APROBACION", "APROBADA_INTERNA"}) {
            BusinessException error = assertThrows(BusinessException.class, () ->
                    ReglasAprobacionCotizacion.validarCambioLibre("BORRADOR", destino, null));
            assertEquals(ReglasAprobacionCotizacion.APROBACION_INTERNA_OBLIGATORIA, error.getMessage());
        }
    }

    @Test
    void borradorNoSaltaAAprobadaRechazadaNiVencida() {
        for (String destino : new String[] {"ACEPTADA_CLIENTE", "APROBADA", "RECHAZADA", "VENCIDA"}) {
            BusinessException error = assertThrows(BusinessException.class, () ->
                    ReglasAprobacionCotizacion.validarCambioLibre("BORRADOR", destino, "motivo"));
            assertEquals(ReglasAprobacionCotizacion.DESTINO_NO_PERMITIDO, error.getMessage());
        }
    }

    @Test
    void enviadaPasaANegociacionYAnuladaExigeMotivo() {
        assertDoesNotThrow(() ->
                ReglasAprobacionCotizacion.validarCambioLibre("ENVIADA", "EN_NEGOCIACION", null));
        BusinessException sinMotivo = assertThrows(BusinessException.class, () ->
                ReglasAprobacionCotizacion.validarCambioLibre("BORRADOR", "ANULADA", "  "));
        assertEquals(ReglasAprobacionCotizacion.MOTIVO_ANULACION_OBLIGATORIO, sinMotivo.getMessage());
        assertDoesNotThrow(() ->
                ReglasAprobacionCotizacion.validarCambioLibre("ENVIADA", "ANULADA", "El cliente desistió"));
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
    void elPatchYaNoAbreLosEstadosDelCliente() {
        for (String[] salto : new String[][] {
                {"EN_NEGOCIACION", "ACEPTADA_CLIENTE"},
                {"EN_NEGOCIACION", "APROBADA"},
                {"ENVIADA", "RECHAZADA"},
                {"ENVIADA", "VENCIDA"}
        }) {
            BusinessException error = assertThrows(BusinessException.class, () ->
                    ReglasAprobacionCotizacion.validarCambioLibre(salto[0], salto[1], "motivo"));
            assertEquals(ReglasAprobacionCotizacion.DESTINO_NO_PERMITIDO, error.getMessage());
        }
    }
}
