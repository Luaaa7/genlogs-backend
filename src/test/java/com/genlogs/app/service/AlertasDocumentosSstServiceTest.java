package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.enums.TipoSstDespacho;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.DespachoSst;
import com.genlogs.app.repository.AlertaSstRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertasDocumentosSstServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    @Mock private AlertaSstRepository alertas;
    @Mock private NotificacionService notificaciones;

    private AlertasDocumentosSstService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlertasDocumentosSstService(alertas, notificaciones);
        when(alertas.contarAvisos(anyString(), anyString())).thenReturn(0L);
    }

    @Test
    void losTramosSonDe30_15y7Dias() {
        assertNull(AlertasDocumentosSstService.umbralDe(0));
        assertEquals(7, AlertasDocumentosSstService.umbralDe(7));
        assertEquals(15, AlertasDocumentosSstService.umbralDe(8));
        assertEquals(15, AlertasDocumentosSstService.umbralDe(15));
        assertEquals(30, AlertasDocumentosSstService.umbralDe(16));
        assertEquals(30, AlertasDocumentosSstService.umbralDe(30));
        assertNull(AlertasDocumentosSstService.umbralDe(31));
        assertNull(AlertasDocumentosSstService.umbralDe(-1));
    }

    @Test
    void buscaLosQueVencenEnLosProximos30DiasSinLosEntregados() {
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of());

        servicio.avisar(HOY);

        verify(alertas).porVencer(HOY.plusDays(1), HOY.plusDays(30), EtapaDespacho.ENTREGADO);
    }

    @Test
    void avisaAlSupervisorSstPorCampanaYCorreo() {
        when(alertas.porVencer(any(), any(), any()))
                .thenReturn(List.of(documento(TipoSstDespacho.SCTR, 5L, HOY.plusDays(30), "SCTR-889")));

        AlertasDocumentosSstService.Resultado resultado = servicio.avisar(HOY);

        String titulo = "SCTR del despacho #5 vence el 12/12/2026 (aviso de 30 días)";
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).avisarRol(eq(RolSistema.SUPERVISOR_SST), eq(TipoAviso.DOCUMENTO_POR_VENCER),
                eq(titulo), mensaje.capture(), eq("/ordenes-compra"), eq(true));
        verify(notificaciones, never()).avisarRol(eq(RolSistema.ASISTENTE_LOGISTICO), any(), anyString(),
                anyString(), anyString(), anyBoolean());
        assertTrue(mensaje.getValue().startsWith("Faltan 30 días"));
        assertTrue(mensaje.getValue().contains("SCTR-889"));
        assertEquals(new AlertasDocumentosSstService.Resultado(1, 1, 0, 0), resultado);
    }

    @Test
    void cadaDocumentoRecibeElAvisoDeSuTramo() {
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of(
                documento(TipoSstDespacho.PASE, 1L, HOY.plusDays(20), null),
                documento(TipoSstDespacho.PASE, 2L, HOY.plusDays(10), null),
                documento(TipoSstDespacho.SCTR, 3L, HOY.plusDays(7), null)));

        servicio.avisar(HOY);

        verify(notificaciones).avisarRol(any(), any(),
                eq("PASE del despacho #1 vence el 02/12/2026 (aviso de 30 días)"), anyString(), anyString(), anyBoolean());
        verify(notificaciones).avisarRol(any(), any(),
                eq("PASE del despacho #2 vence el 22/11/2026 (aviso de 15 días)"), anyString(), anyString(), anyBoolean());
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).avisarRol(any(), any(),
                eq("SCTR del despacho #3 vence el 19/11/2026 (aviso de 7 días)"), mensaje.capture(), anyString(), anyBoolean());
        assertTrue(mensaje.getValue().startsWith("Faltan 7 días"));
    }

    @Test
    void noRepiteUnAvisoQueYaExiste() {
        DespachoSst pase = documento(TipoSstDespacho.PASE, 1L, HOY.plusDays(12), null);
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of(pase));
        when(alertas.contarAvisos(TipoAviso.DOCUMENTO_POR_VENCER.name(),
                "PASE del despacho #1 vence el 24/11/2026 (aviso de 15 días)")).thenReturn(2L);

        AlertasDocumentosSstService.Resultado resultado = servicio.avisar(HOY);

        verify(notificaciones, never()).avisarRol(any(), any(), anyString(), anyString(), anyString(), anyBoolean());
        assertEquals(new AlertasDocumentosSstService.Resultado(1, 0, 1, 0), resultado);
    }

    @Test
    void siRenuevanElDocumentoConOtraFechaVuelveAAvisar() {
        String anterior = AlertasDocumentosSstService.titulo(documento(TipoSstDespacho.PASE, 1L, HOY.plusDays(5), null), 7);
        String renovado = AlertasDocumentosSstService.titulo(documento(TipoSstDespacho.PASE, 1L, HOY.plusDays(25), null), 30);

        assertTrue(!anterior.equals(renovado));
    }

    @Test
    void unDocumentoQueFallaNoDetieneALosDemas() {
        DespachoSst primero = documento(TipoSstDespacho.PASE, 1L, HOY.plusDays(3), null);
        DespachoSst segundo = documento(TipoSstDespacho.SCTR, 2L, HOY.plusDays(3), null);
        when(alertas.porVencer(any(), any(), any())).thenReturn(List.of(primero, segundo));
        String tituloPrimero = AlertasDocumentosSstService.titulo(primero, 7);
        doThrow(new IllegalStateException("BD caída")).when(notificaciones)
                .avisarRol(any(), any(), eq(tituloPrimero), anyString(), anyString(), anyBoolean());

        AlertasDocumentosSstService.Resultado resultado = servicio.avisar(HOY);

        verify(notificaciones).avisarRol(any(), any(),
                eq(AlertasDocumentosSstService.titulo(segundo, 7)), anyString(), anyString(), anyBoolean());
        assertEquals(new AlertasDocumentosSstService.Resultado(2, 1, 0, 1), resultado);
    }

    @Test
    void elTituloCabeEnLaColumnaDeNotificacion() {
        String titulo = AlertasDocumentosSstService.titulo(
                documento(TipoSstDespacho.SCTR, 9_999_999_999L, HOY.plusDays(30), null), 30);

        assertTrue(titulo.length() <= 150, titulo);
    }

    private static DespachoSst documento(TipoSstDespacho tipo, Long idDespacho, LocalDate vence, String referencia) {
        Despacho despacho = new Despacho();
        despacho.setIdDespacho(idDespacho);
        despacho.setEtapa(EtapaDespacho.EN_PREPARACION);
        DespachoSst documento = new DespachoSst();
        documento.setDespacho(despacho);
        documento.setTipo(tipo);
        documento.setFechaVencimiento(vence);
        documento.setReferencia(referencia);
        return documento;
    }
}
