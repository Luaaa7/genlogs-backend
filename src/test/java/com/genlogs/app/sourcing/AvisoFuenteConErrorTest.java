package com.genlogs.app.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.service.NotificacionService;

@ExtendWith(MockitoExtension.class)
class AvisoFuenteConErrorTest {

    @Mock private NotificacionRepository notificaciones;
    @Mock private NotificacionService avisos;

    private AvisoFuenteConError servicio;

    @BeforeEach
    void reloj() {
        servicio = new AvisoFuenteConError(notificaciones, avisos);
        servicio.setClock(Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneId.of("America/Lima")));
    }

    @Test
    void conErrorAvisaAlJefeSoloPorCampanita() {
        noAvisadaHoy();

        assertThat(servicio.avisarSiCorresponde("ebay", "CON_ERROR", "Sesión vencida")).isTrue();

        verify(avisos).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.FUENTE_CON_ERROR),
                eq("Fuente EBAY con error"), any(), eq("/configuracion/fuentes?fuente=EBAY"), eq(false));
    }

    @Test
    void requiereVerificacionTambienAvisa() {
        noAvisadaHoy();

        assertThat(servicio.avisarSiCorresponde("AMAZON", "REQUIERE_VERIFICACION", null)).isTrue();

        verify(avisos).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.FUENTE_CON_ERROR),
                eq("Fuente AMAZON: completar verificación"), any(), eq("/configuracion/fuentes?fuente=AMAZON"),
                eq(false));
    }

    @Test
    void laMismaFuenteNoSeAvisaDosVecesElMismoDia() {
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                "FUENTE_CON_ERROR", "/configuracion/fuentes?fuente=EBAY",
                LocalDateTime.of(2026, 11, 13, 0, 0), LocalDateTime.of(2026, 11, 14, 0, 0))).thenReturn(true);

        assertThat(servicio.avisarSiCorresponde("EBAY", "CON_ERROR", "otra vez")).isFalse();

        verify(avisos, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void activaOSinProbarNoAvisan() {
        assertThat(servicio.avisarSiCorresponde("EBAY", "ACTIVA", null)).isFalse();
        assertThat(servicio.avisarSiCorresponde("EBAY", "SIN_PROBAR", null)).isFalse();
        assertThat(servicio.avisarSiCorresponde(" ", "CON_ERROR", null)).isFalse();

        verify(avisos, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void unFalloAlAvisarNoRompeAQuienLlama() {
        noAvisadaHoy();
        when(avisos.avisarRol(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("BD caída"));

        assertThat(servicio.avisarSiCorresponde("ALIBABA", "CON_ERROR", null)).isFalse();
    }

    @Test
    void elDetalleSeRecortaYNoSeRepiteElMensajeBase() {
        String largo = "x".repeat(500);

        String mensaje = AvisoFuenteConError.mensaje("EBAY", "CON_ERROR", largo);

        assertThat(mensaje).startsWith("La búsqueda sigue con las demás fuentes.");
        assertThat(mensaje).endsWith("…");
        assertThat(mensaje.length()).isLessThan(400);
    }

    private void noAvisadaHoy() {
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), any(), any(), any())).thenReturn(false);
    }
}
