package com.genlogs.app.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

/** Complemento de FacturasVencidasTest: un error en una factura no detiene la tarea. */
@ExtendWith(MockitoExtension.class)
class FacturasVencidasErroresTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

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
    void siFallaElAvisoDeUnaFacturaSigueConLasDemas() {
        Facturacion primera = factura(7L, "00000012");
        Facturacion segunda = factura(8L, "00000013");
        when(facturas.vencidas(HOY, List.of("EMITIDA", "PARCIAL"))).thenReturn(List.of(primera, segunda));
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), any(), any(), any())).thenReturn(false);
        doThrow(new IllegalStateException("BD caída")).when(avisos)
                .avisarRol(any(), any(), anyString(), anyString(), eq("/facturacion/7"), anyBoolean());

        servicio.avisarVencidas();

        verify(avisos).avisarRol(RolSistema.CONTADOR_GENERAL, TipoAviso.FACTURA_VENCIDA, "Factura vencida",
                "F001-00000013 venció el 2026-11-11", "/facturacion/8", true);
    }

    @Test
    void siFallaLaRevisionDeDuplicadosTambienSigue() {
        Facturacion primera = factura(7L, "00000012");
        Facturacion segunda = factura(8L, "00000013");
        when(facturas.vencidas(HOY, List.of("EMITIDA", "PARCIAL"))).thenReturn(List.of(primera, segunda));
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), eq("/facturacion/7"), any(), any())).thenThrow(new IllegalStateException("timeout"));
        when(notificaciones.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), eq("/facturacion/8"), any(), any())).thenReturn(false);

        servicio.avisarVencidas();

        verify(avisos).avisarRol(RolSistema.CONTADOR_GENERAL, TipoAviso.FACTURA_VENCIDA, "Factura vencida",
                "F001-00000013 venció el 2026-11-11", "/facturacion/8", true);
    }

    private static Facturacion factura(Long id, String numero) {
        return Facturacion.builder()
                .idFacturacion(id)
                .serieComprobante("F001")
                .numeroComprobante(numero)
                .fechaVencimiento(HOY.minusDays(1))
                .estadoFacturacion(EstadoFacturacion.builder().codigoEstado("EMITIDA").nombreEstado("EMITIDA").build())
                .build();
    }
}
