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
import com.genlogs.app.model.EstadoFacturacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.NotificacionRepository;

@ExtendWith(MockitoExtension.class)
class FacturasVencidasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);
    private static final LocalDate AYER = HOY.minusDays(1);

    @Mock private FacturacionRepository facturas;
    @Mock private NotificacionRepository notificaciones;
    @Mock private NotificacionService avisos;

    private FacturasVencidasService servicio;

    @BeforeEach
    void reloj() {
        servicio = new FacturasVencidasService(facturas, notificaciones, avisos);
        servicio.setClock(Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Lima")));
    }

    @Test
    void unaEmitidaDeAyerAvisaAlContadorPorCampanitaYCorreo() {
        Facturacion emitida = factura("EMITIDA", AYER);
        when(facturas.vencidas(HOY, List.of("EMITIDA", "PARCIAL"))).thenReturn(List.of(emitida));
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), any(), any(), any())).thenReturn(false);

        servicio.avisarVencidas();

        verify(avisos).avisarRol(RolSistema.CONTADOR_GENERAL, TipoAviso.FACTURA_VENCIDA, "Factura vencida",
                "F001-00000012 venció el 2026-11-11", "/facturacion/7", true);
    }

    @Test
    void unaPagadaVencidaNoAvisa() {
        when(facturas.vencidas(HOY, List.of("EMITIDA", "PARCIAL"))).thenReturn(List.of(factura("PAGADA", AYER)));

        servicio.avisarVencidas();

        verify(avisos, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void unaEmitidaQueVenceHoyNoAvisa() {
        when(facturas.vencidas(HOY, List.of("EMITIDA", "PARCIAL"))).thenReturn(List.of(factura("EMITIDA", HOY)));

        servicio.avisarVencidas();

        verify(avisos, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void correrlaOtraVezElMismoDiaNoRepiteElAviso() {
        Facturacion emitida = factura("EMITIDA", AYER);
        when(facturas.vencidas(HOY, List.of("EMITIDA", "PARCIAL"))).thenReturn(List.of(emitida));
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), any(), any(), any())).thenReturn(false, true);

        servicio.avisarVencidas();
        servicio.avisarVencidas();

        verify(avisos, times(1)).avisarRol(RolSistema.CONTADOR_GENERAL, TipoAviso.FACTURA_VENCIDA, "Factura vencida",
                "F001-00000012 venció el 2026-11-11", "/facturacion/7", true);
    }

    private static Facturacion factura(String estado, LocalDate vencimiento) {
        return Facturacion.builder()
                .idFacturacion(7L)
                .serieComprobante("F001")
                .numeroComprobante("00000012")
                .fechaVencimiento(vencimiento)
                .estadoFacturacion(EstadoFacturacion.builder().codigoEstado(estado).nombreEstado(estado).build())
                .build();
    }
}
