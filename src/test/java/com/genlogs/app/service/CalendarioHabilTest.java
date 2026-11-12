package com.genlogs.app.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Horario hábil de GENLOGS: lunes a viernes 08:00-18:00 y sábado 08:00-13:00.
 * Regla: el plazo empieza a contar el día hábil siguiente a la recepción y vence
 * al cierre del N-ésimo día hábil. Lo recibido fuera de horario cuenta como
 * recibido en la siguiente apertura.
 * Fechas de noviembre 2026: el 9 es lunes.
 */
class CalendarioHabilTest {

    private final CalendarioHabil calendario = new CalendarioHabil();

    private static LocalDateTime nov(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 11, dia, hora, minuto);
    }

    @Test
    void plazoComun_lunesEnHorario_venceMartesAlCierre() {
        assertEquals(nov(10, 18, 0), calendario.fechaLimite(nov(9, 10, 0), 1));
    }

    @Test
    void plazoImportado_lunesEnHorario_venceJuevesAlCierre() {
        assertEquals(nov(12, 18, 0), calendario.fechaLimite(nov(9, 10, 0), 3));
    }

    @Test
    void plazoEspecial_lunesEnHorario_venceSabadoAlCierreDelSabado() {
        assertEquals(nov(14, 13, 0), calendario.fechaLimite(nov(9, 10, 0), 5));
    }

    @Test
    void viernesEnHorario_unDia_venceSabadoALas13() {
        assertEquals(nov(14, 13, 0), calendario.fechaLimite(nov(13, 10, 0), 1));
    }

    @Test
    void sabadoEnHorario_unDia_saltaElDomingoYVenceLunes() {
        assertEquals(nov(16, 18, 0), calendario.fechaLimite(nov(14, 10, 0), 1));
    }

    @Test
    void lunesDespuesDelCierre_cuentaComoRecibidoElMartes() {
        assertEquals(nov(11, 18, 0), calendario.fechaLimite(nov(9, 19, 0), 1));
    }

    @Test
    void lunesAntesDeAbrir_cuentaComoRecibidoElMismoLunes() {
        assertEquals(nov(10, 18, 0), calendario.fechaLimite(nov(9, 7, 0), 1));
    }

    @Test
    void lunesJustoAlCierre_cuentaComoRecibidoElMartes() {
        assertEquals(nov(11, 18, 0), calendario.fechaLimite(nov(9, 18, 0), 1));
    }

    @Test
    void sabadoDespuesDeLas13_cuentaComoRecibidoElLunes() {
        assertEquals(nov(17, 18, 0), calendario.fechaLimite(nov(14, 14, 0), 1));
    }

    @Test
    void domingo_cuentaComoRecibidoElLunes() {
        assertEquals(nov(17, 18, 0), calendario.fechaLimite(nov(15, 12, 0), 1));
    }

    @Test
    void diasMenoresAUno_lanzaError() {
        assertThrows(IllegalArgumentException.class, () -> calendario.fechaLimite(nov(9, 10, 0), 0));
    }

    @Test
    void recibidoNulo_lanzaError() {
        assertThrows(IllegalArgumentException.class, () -> calendario.fechaLimite(null, 1));
    }

    // ---- Feriados (D-18) ----

    private final CalendarioHabil conFeriados = new CalendarioHabil(Set.of(
            LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9), LocalDate.of(2027, 5, 1))::contains);

    @Test
    void feriadosSeguidos_noCuentanComoDiasHabiles() {
        // lunes 7/12/2026; martes 8 y miércoles 9 son feriados
        assertEquals(LocalDateTime.of(2026, 12, 10, 18, 0),
                conFeriados.fechaLimite(LocalDateTime.of(2026, 12, 7, 10, 0), 1));
    }

    @Test
    void recibidoEnFeriado_cuentaDesdeElSiguienteDiaHabil() {
        // llega el martes 8 (feriado): se toma como recibido el jueves 10
        assertEquals(LocalDateTime.of(2026, 12, 11, 18, 0),
                conFeriados.fechaLimite(LocalDateTime.of(2026, 12, 8, 10, 0), 1));
    }

    @Test
    void feriadoEnSabado_saltaHastaElLunes() {
        // viernes 30/4/2027; sábado 1/5 es feriado y el domingo no se atiende
        assertEquals(LocalDateTime.of(2027, 5, 3, 18, 0),
                conFeriados.fechaLimite(LocalDateTime.of(2027, 4, 30, 10, 0), 1));
    }

    @Test
    void sinFeriadosRegistrados_seComportaIgualQueAntes() {
        assertEquals(LocalDateTime.of(2026, 12, 8, 18, 0),
                calendario.fechaLimite(LocalDateTime.of(2026, 12, 7, 10, 0), 1));
    }
}
