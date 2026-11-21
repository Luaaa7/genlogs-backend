package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;

class PortalCotizacionPuedeDecidirTest {

    private static final LocalDate VALIDEZ = LocalDate.of(2026, 11, 15);
    /** 23:59 del 15/11 en Lima. En UTC ya es 16/11: la regla vieja lo rechazaría. */
    private static final Clock ULTIMO_MINUTO = Clock.fixed(Instant.parse("2026-11-16T04:59:00Z"), ZoneOffset.UTC);
    /** 00:00 del 16/11 en Lima. */
    private static final Clock DIA_SIGUIENTE = Clock.fixed(Instant.parse("2026-11-16T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void conRelojRealComparaContraElDiaDeLima() {
        LocalDate hoy = LocalDate.now(PlazoDespacho.LIMA);
        try {
            assertDiaDeLima(hoy);
        } catch (AssertionError primera) {
            LocalDate despues = LocalDate.now(PlazoDespacho.LIMA);
            if (hoy.equals(despues)) {
                throw primera;
            }
            assertDiaDeLima(despues);
        }
    }

    @Test
    void elDiaDeValidezInclusiveSigueAbiertoYElSiguienteNo() {
        assertEquals(VALIDEZ, PortalCotizacionService.hoyLima(ULTIMO_MINUTO));
        assertEquals(VALIDEZ.plusDays(1), PortalCotizacionService.hoyLima(DIA_SIGUIENTE));

        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", VALIDEZ),
                PortalCotizacionService.hoyLima(ULTIMO_MINUTO)));
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("EN_NEGOCIACION", VALIDEZ),
                PortalCotizacionService.hoyLima(ULTIMO_MINUTO)));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", VALIDEZ),
                PortalCotizacionService.hoyLima(DIA_SIGUIENTE)));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("EN_NEGOCIACION", VALIDEZ),
                PortalCotizacionService.hoyLima(DIA_SIGUIENTE)));
    }

    @Test
    void sinFechaDeValidezSePuedeDecidir() {
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", null),
                PortalCotizacionService.hoyLima(DIA_SIGUIENTE)));
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("EN_NEGOCIACION", null),
                PortalCotizacionService.hoyLima(ULTIMO_MINUTO)));
    }

    @Test
    void unEstadoCerradoNoSeDecideAunqueLaFechaSirva() {
        LocalDate hoy = PortalCotizacionService.hoyLima(ULTIMO_MINUTO);
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("ACEPTADA_CLIENTE", VALIDEZ), hoy));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("APROBADA", VALIDEZ), hoy));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("RECHAZADA", VALIDEZ), hoy));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("VENCIDA", VALIDEZ), hoy));
    }

    private static void assertDiaDeLima(LocalDate hoy) {
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", hoy)));
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("EN_NEGOCIACION", hoy)));
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", null)));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", hoy.minusDays(1))));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("APROBADA", hoy)));
        assertEquals(hoy, LocalDate.now(PlazoDespacho.LIMA));
    }

    private static Cotizacion cotizacion(String codigo, LocalDate fechaValidez) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(codigo);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setFechaValidez(fechaValidez);
        return cotizacion;
    }
}
