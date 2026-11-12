package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;

class PortalCotizacionPuedeDecidirTest {

    @Test
    void enviadaDeHoyOSinFechaPuedeDecidirYAyerOAprobadaNo() {
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", LocalDate.now())));
        assertTrue(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", null)));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("ENVIADA", LocalDate.now().minusDays(1))));
        assertFalse(PortalCotizacionService.puedeDecidir(cotizacion("APROBADA", LocalDate.now())));
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
