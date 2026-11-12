package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.enums.OrigenRepuesto;

@ExtendWith(MockitoExtension.class)
class PlazoDespachoTest {

    @Mock
    private FeriadoService feriados;

    @Test
    void nacionalSonVeinticuatroYCuarentaYOchoHoras() {
        PlazoDespacho plazo = new PlazoDespacho(feriados);
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 13, 10, 0);

        PlazoDespacho.Rango rango = plazo.calcular(OrigenRepuesto.NACIONAL, inicio);

        assertEquals(inicio.plusHours(24), rango.desde());
        assertEquals(inicio.plusHours(48), rango.hasta());
    }

    @Test
    void importadoSaltaElFinDeSemana() {
        when(feriados.esFeriado(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        PlazoDespacho plazo = new PlazoDespacho(feriados);
        LocalDateTime viernes = LocalDateTime.of(2026, 3, 13, 10, 0);

        PlazoDespacho.Rango rango = plazo.calcular(OrigenRepuesto.IMPORTADO, viernes);

        assertEquals(LocalDate.of(2026, 3, 20), rango.desde().toLocalDate());
        assertEquals(LocalDate.of(2026, 3, 24), rango.hasta().toLocalDate());
    }

    @Test
    void importadoSaltaElFeriado() {
        LocalDate lunes = LocalDate.of(2026, 3, 16);
        when(feriados.esFeriado(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> lunes.equals(inv.getArgument(0)));
        PlazoDespacho plazo = new PlazoDespacho(feriados);

        PlazoDespacho.Rango rango = plazo.calcular(
                OrigenRepuesto.IMPORTADO, LocalDateTime.of(2026, 3, 13, 10, 0));

        assertEquals(LocalDate.of(2026, 3, 23), rango.desde().toLocalDate());
        assertEquals(LocalDate.of(2026, 3, 25), rango.hasta().toLocalDate());
    }
}
