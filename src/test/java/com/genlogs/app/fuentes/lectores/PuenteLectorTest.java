package com.genlogs.app.fuentes.lectores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.genlogs.app.sourcing.lector.ContratoLector;

/** ME: la sesión que guarda L1 llega al servicio de lectura en su formato. */
class PuenteLectorTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-15T17:00:00Z"), ZoneOffset.UTC);

    @Test
    void aceptaLaSesionConSuVencimiento() {
        ContratoLector.Sesion s = PuenteLector.sesion(
                "{\"validaHasta\":\"2026-11-15T20:00:00Z\",\"storageState\":{\"cookies\":[],\"origins\":[]}}", RELOJ);
        assertThat(s.validaHasta()).isEqualTo("2026-11-15T20:00:00Z");
        assertThat(s.storageState().has("cookies")).isTrue();
    }

    @Test
    void sinVencimientoUsaMediaHora() {
        ContratoLector.Sesion s = PuenteLector.sesion("{\"cookies\":[],\"origins\":[]}", RELOJ);
        assertThat(s.validaHasta()).isEqualTo("2026-11-15T17:30:00Z");
        ContratoLector.Sesion conFechaRara = PuenteLector.sesion(
                "{\"validaHasta\":\"mañana\",\"storageState\":{\"cookies\":[]}}", RELOJ);
        assertThat(conFechaRara.validaHasta()).isEqualTo("2026-11-15T17:30:00Z");
    }

    @Test
    void sinSesionSeLeeSinSesion() {
        assertThat(PuenteLector.sesion(null, RELOJ)).isNull();
        assertThat(PuenteLector.sesion("  ", RELOJ)).isNull();
    }

    @Test
    void unaSesionSinCookiesNoSirve() {
        assertThatThrownBy(() -> PuenteLector.sesion("no-es-json", RELOJ)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PuenteLector.sesion("[1,2]", RELOJ)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PuenteLector.sesion("{\"token\":\"x\"}", RELOJ))
                .isInstanceOf(IllegalArgumentException.class);
    }
}