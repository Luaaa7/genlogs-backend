package com.genlogs.app.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.enums.TipoSstDespacho;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.DespachoSst;
import com.genlogs.app.repository.AlertaSstRepository;

@ExtendWith(MockitoExtension.class)
class VencimientoPaseSctrTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    @Mock private AlertaSstRepository alertas;
    @Mock private NotificacionService avisos;

    private AlertasDocumentosSstService servicio;

    @BeforeEach
    void reloj() {
        servicio = new AlertasDocumentosSstService(alertas, avisos);
        servicio.setClock(Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Lima")));
    }

    @Test
    void aTreintaQuinceYSieteDiasAvisaUnaSolaVez() {
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of(
                documento(TipoSstDespacho.SCTR, 1L, HOY.plusDays(30)),
                documento(TipoSstDespacho.PASE, 2L, HOY.plusDays(15)),
                documento(TipoSstDespacho.PASE, 3L, HOY.plusDays(7))));

        servicio.avisar();

        verify(avisos).avisarRol(RolSistema.SUPERVISOR_SST, TipoAviso.DOCUMENTO_POR_VENCER,
                "SCTR del despacho #1 vence el 12/12/2026 (aviso de 30 días)",
                "Faltan 30 días para que venza el SCTR del despacho #1. Renuévalo antes de que bloquee el despacho.",
                "/ordenes-compra", true);
        verify(avisos).avisarRol(RolSistema.SUPERVISOR_SST, TipoAviso.DOCUMENTO_POR_VENCER,
                "PASE del despacho #2 vence el 27/11/2026 (aviso de 15 días)",
                "Faltan 15 días para que venza el PASE del despacho #2. Renuévalo antes de que bloquee el despacho.",
                "/ordenes-compra", true);
        verify(avisos).avisarRol(RolSistema.SUPERVISOR_SST, TipoAviso.DOCUMENTO_POR_VENCER,
                "PASE del despacho #3 vence el 19/11/2026 (aviso de 7 días)",
                "Faltan 7 días para que venza el PASE del despacho #3. Renuévalo antes de que bloquee el despacho.",
                "/ordenes-compra", true);
        verify(avisos, times(3)).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void correrlaOtraVezElMismoDiaNoRepiteElAviso() {
        when(alertas.porVencer(any(), any(), any()))
                .thenReturn(List.of(documento(TipoSstDespacho.SCTR, 1L, HOY.plusDays(30))));
        when(alertas.contarAvisos(any(), any())).thenReturn(0L, 1L);

        servicio.avisar();
        servicio.avisar();

        verify(avisos, times(1)).avisarRol(RolSistema.SUPERVISOR_SST, TipoAviso.DOCUMENTO_POR_VENCER,
                "SCTR del despacho #1 vence el 12/12/2026 (aviso de 30 días)",
                "Faltan 30 días para que venza el SCTR del despacho #1. Renuévalo antes de que bloquee el despacho.",
                "/ordenes-compra", true);
    }

    @Test
    void aTreintaYUnDiasHoyYUnoVencidoNoAvisan() {
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of(
                documento(TipoSstDespacho.PASE, 1L, HOY.plusDays(31)),
                documento(TipoSstDespacho.SCTR, 2L, HOY),
                documento(TipoSstDespacho.SCTR, 3L, HOY.minusDays(1))));

        servicio.avisar();

        verify(avisos, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void elPaseYElSctrDelMismoDespachoSeEvaluanPorSeparado() {
        Despacho despacho = new Despacho();
        despacho.setIdDespacho(8L);
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of(
                documento(despacho, TipoSstDespacho.PASE, HOY.plusDays(15)),
                documento(despacho, TipoSstDespacho.SCTR, HOY.plusDays(7))));

        servicio.avisar();

        verify(avisos).avisarRol(RolSistema.SUPERVISOR_SST, TipoAviso.DOCUMENTO_POR_VENCER,
                "PASE del despacho #8 vence el 27/11/2026 (aviso de 15 días)",
                "Faltan 15 días para que venza el PASE del despacho #8. Renuévalo antes de que bloquee el despacho.",
                "/ordenes-compra", true);
        verify(avisos).avisarRol(RolSistema.SUPERVISOR_SST, TipoAviso.DOCUMENTO_POR_VENCER,
                "SCTR del despacho #8 vence el 19/11/2026 (aviso de 7 días)",
                "Faltan 7 días para que venza el SCTR del despacho #8. Renuévalo antes de que bloquee el despacho.",
                "/ordenes-compra", true);
    }

    private static DespachoSst documento(TipoSstDespacho tipo, Long idDespacho, LocalDate vence) {
        Despacho despacho = new Despacho();
        despacho.setIdDespacho(idDespacho);
        return documento(despacho, tipo, vence);
    }

    private static DespachoSst documento(Despacho despacho, TipoSstDespacho tipo, LocalDate vence) {
        DespachoSst documento = new DespachoSst();
        documento.setDespacho(despacho);
        documento.setTipo(tipo);
        documento.setFechaVencimiento(vence);
        return documento;
    }
}
