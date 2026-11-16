package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.TasaIgv;
import com.genlogs.app.repository.TasaIgvRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TasaIgvVigenciaTest {

    @Autowired private TasaIgvService servicio;
    @Autowired private TasaIgvRepository tasas;

    @Test
    void elDiaAnteriorUsaLaTasaViejaYElDiaDeVigenciaUsaLaNueva() {
        tasas.save(tasa("16.00", LocalDate.of(2026, 11, 1)));
        tasas.save(tasa("18.00", LocalDate.of(2026, 11, 16)));

        assertEquals(0, new BigDecimal("16.00").compareTo(servicio.porcentajeVigente(LocalDate.of(2026, 11, 15))));
        assertEquals(0, new BigDecimal("18.00").compareTo(servicio.porcentajeVigente(LocalDate.of(2026, 11, 16))));
    }

    private static TasaIgv tasa(String porcentaje, LocalDate vigenteDesde) {
        return TasaIgv.builder()
                .porcentaje(new BigDecimal(porcentaje))
                .vigenteDesde(vigenteDesde)
                .userCreate("test")
                .build();
    }
}
