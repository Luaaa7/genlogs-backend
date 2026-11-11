package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Feriado;
import com.genlogs.app.repository.FeriadoRepository;

/** Feriados editables por el Jefe Administrativo (D-18), contra H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeriadoServiceIntegracionTest {

    @Autowired
    private FeriadoService feriadoService;

    @Autowired
    private FeriadoRepository feriadoRepository;

    @Autowired
    private CalendarioHabil calendarioHabil;

    /** Un lunes al menos un mes en el futuro, para que siempre sea "fecha futura". */
    private final LocalDate lunesFuturo = LocalDate.now().plusMonths(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void sesion() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jefe.adm", null, List.of()));
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crearFeriadoFuturo_quedaActivoYCuentaComoFeriado() {
        Feriado f = feriadoService.crear(lunesFuturo, "Día no laborable de prueba");

        assertEquals("A", f.getStatus());
        assertEquals("jefe.adm", f.getUserCreate());
        assertTrue(feriadoService.esFeriado(lunesFuturo));
    }

    @Test
    void crearFeriadoEnFechaPasadaOHoy_seRechaza() {
        assertThrows(BusinessException.class, () -> feriadoService.crear(LocalDate.now(), "Hoy"));
        assertThrows(BusinessException.class, () -> feriadoService.crear(LocalDate.now().minusDays(3), "Pasado"));
    }

    @Test
    void crearFeriadoRepetido_seRechaza() {
        feriadoService.crear(lunesFuturo, "Primero");
        assertThrows(BusinessException.class, () -> feriadoService.crear(lunesFuturo, "Repetido"));
    }

    @Test
    void crearFeriadoSinDescripcion_seRechaza() {
        assertThrows(BusinessException.class, () -> feriadoService.crear(lunesFuturo, "  "));
    }

    @Test
    void darDeBajaFeriadoFuturo_loDejaInactivoYDejaDeContar() {
        Feriado f = feriadoService.crear(lunesFuturo, "Se cancela");

        feriadoService.darDeBaja(f.getIdFeriado());

        assertEquals("I", feriadoRepository.findById(f.getIdFeriado()).orElseThrow().getStatus());
        assertFalse(feriadoService.esFeriado(lunesFuturo));
    }

    @Test
    void darDeBajaFeriadoPasado_seRechazaPorqueYaAfectoPlazos() {
        Feriado pasado = new Feriado();
        pasado.setFecha(LocalDate.now().minusDays(10));
        pasado.setDescripcion("Ya pasó");
        pasado.setUserCreate("sistema");
        pasado.setProcessCreate("prueba");
        pasado = feriadoRepository.save(pasado);
        Integer id = pasado.getIdFeriado();

        assertThrows(BusinessException.class, () -> feriadoService.darDeBaja(id));
    }

    @Test
    void despuesDeDarDeBaja_sePuedeVolverACrearLaMismaFecha() {
        Feriado f = feriadoService.crear(lunesFuturo, "Primero");
        feriadoService.darDeBaja(f.getIdFeriado());

        Feriado otra = feriadoService.crear(lunesFuturo, "De nuevo");

        assertEquals("A", otra.getStatus());
    }

    @Test
    void listarDesde_devuelveSoloActivosEnOrden() {
        feriadoService.crear(lunesFuturo.plusDays(7), "Segundo");
        feriadoService.crear(lunesFuturo, "Primero");
        Feriado baja = feriadoService.crear(lunesFuturo.plusDays(3), "Dado de baja");
        feriadoService.darDeBaja(baja.getIdFeriado());

        List<Feriado> lista = feriadoService.listarDesde(lunesFuturo);

        assertEquals(List.of(lunesFuturo, lunesFuturo.plusDays(7)), lista.stream().map(Feriado::getFecha).toList());
    }

    @Test
    void elCalendarioDeSpringUsaLosFeriadosRegistrados() {
        // Pedido común el lunes; si el martes es feriado, vence el miércoles.
        feriadoService.crear(lunesFuturo.plusDays(1), "Martes feriado");

        LocalDateTime limite = calendarioHabil.fechaLimite(lunesFuturo.atTime(10, 0), 1);

        assertEquals(lunesFuturo.plusDays(2).atTime(18, 0), limite);
    }
}
