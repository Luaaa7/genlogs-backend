package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.enums.FuenteTipoCambio;

@ExtendWith(MockitoExtension.class)
class TipoCambioCotizacionTest {

    @Mock
    private TipoCambioService tipoCambioService;

    @Test
    void usaElValorDelDia() {
        when(tipoCambioService.delDia()).thenReturn(Optional.of(dia(true, "3.7100")));

        assertEquals(0, new BigDecimal("3.7100").compareTo(lector().vigente()));
    }

    @Test
    void siHoyNoHayValorUsaElUltimoDisponible() {
        when(tipoCambioService.delDia()).thenReturn(Optional.of(dia(false, "3.6800")));

        assertEquals(0, new BigDecimal("3.6800").compareTo(lector().vigente()));
    }

    @Test
    void siLaConsultaFallaNoRompe() {
        when(tipoCambioService.delDia()).thenThrow(new RuntimeException("sin conexión"));

        assertNull(lector().vigente());
    }

    @Test
    void sinFilasDejaElCampoVacio() {
        when(tipoCambioService.delDia()).thenReturn(Optional.empty());

        assertNull(lector().vigente());
    }

    private TipoCambioCotizacion lector() {
        return new TipoCambioCotizacion(tipoCambioService);
    }

    private static TipoCambioDia dia(boolean esDeHoy, String valor) {
        return new TipoCambioDia(
                LocalDate.of(2026, 11, 11),
                "USD",
                new BigDecimal(valor),
                FuenteTipoCambio.SUNAT,
                esDeHoy);
    }
}
