package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AvisoOrdenNoCreadaTest {

    @Mock private CotizacionRepository cotizacionRepository;
    @Mock private SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionService notificacionService;

    @InjectMocks
    private AvisoOrdenNoCreada aviso;

    @Test
    void avisaSoloAEsosDosRolesYElTextoNoTraeMargenNiCosto() {
        when(cotizacionRepository.findConClienteParaAviso(7L)).thenReturn(Optional.of(cotizacion()));

        aviso.avisar(7L);

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificacionService).avisarRol(
                eq(RolSistema.ASISTENTE_ADMINISTRATIVO), eq(TipoAviso.ORDEN_NO_CREADA),
                anyString(), mensaje.capture(), eq("/cotizaciones/7"), eq(false));
        verify(notificacionService).avisarRol(
                eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.ORDEN_NO_CREADA),
                anyString(), mensaje.capture(), eq("/cotizaciones/7"), eq(true));
        verify(notificacionService, never()).avisarRol(
                eq(RolSistema.GERENTE_GENERAL), any(), any(), any(), any(), anyBoolean());

        String texto = String.join(" ", mensaje.getAllValues()).toLowerCase();
        assertTrue(texto.contains("cot-7"));
        assertTrue(texto.contains("mina sur"));
        assertTrue(texto.contains("la orden de compra no se creó y debe registrarse a mano"));
        assertFalse(texto.contains("margen"));
        assertFalse(texto.contains("costo"));
    }

    @Test
    void siFallaUnRolIgualAvisaAlOtro() {
        when(cotizacionRepository.findConClienteParaAviso(7L)).thenReturn(Optional.of(cotizacion()));
        when(notificacionService.avisarRol(
                eq(RolSistema.ASISTENTE_ADMINISTRATIVO), any(), any(), any(), any(), eq(false)))
                .thenThrow(new RuntimeException("asistente"));

        RuntimeException fallo = assertThrows(RuntimeException.class, () -> aviso.avisar(7L));

        assertEquals("asistente", fallo.getMessage());
        verify(notificacionService).avisarRol(
                eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.ORDEN_NO_CREADA),
                anyString(), anyString(), eq("/cotizaciones/7"), eq(true));
    }

    @Test
    void elSeguimientoGuardaElMotivo() {
        when(cotizacionRepository.findConClienteParaAviso(7L)).thenReturn(Optional.of(cotizacion()));

        aviso.registrarSeguimiento(7L, "No existe el estado RECIBIDA configurado");

        ArgumentCaptor<SeguimientoCotizacion> guardado = ArgumentCaptor.forClass(SeguimientoCotizacion.class);
        verify(seguimientoCotizacionRepository).save(guardado.capture());
        String comentario = guardado.getValue().getComentario();
        assertTrue(comentario.contains("No existe el estado RECIBIDA configurado"));
        assertTrue(comentario.contains("la orden de compra no se creó y debe registrarse a mano"));
        assertFalse(comentario.toLowerCase().contains("margen"));
        assertFalse(comentario.toLowerCase().contains("costo"));
    }

    @Test
    void siLaOrdenSeCreaNoHayAvisoNiSeguimientoDeFallo() {
        OrdenCompraService ordenes = org.mockito.Mockito.mock(OrdenCompraService.class);
        AvisoOrdenNoCreada avisoMock = org.mockito.Mockito.mock(AvisoOrdenNoCreada.class);
        CotizacionService servicio = servicio(ordenes, avisoMock);
        when(ordenes.crearAutomatica(5L)).thenReturn(null);

        assertDoesNotThrow(() -> servicio.registrarOrdenSiAprobada(5L, "APROBADA"));

        verify(avisoMock, never()).avisar(any());
        verify(avisoMock, never()).registrarSeguimiento(any(), any());
    }

    @Test
    void siElAvisoFallaIgualDejaSeguimientoYLaLlamadaNoRevienta() {
        OrdenCompraService ordenes = org.mockito.Mockito.mock(OrdenCompraService.class);
        AvisoOrdenNoCreada avisoMock = org.mockito.Mockito.mock(AvisoOrdenNoCreada.class);
        CotizacionService servicio = servicio(ordenes, avisoMock);
        when(ordenes.crearAutomatica(5L)).thenThrow(new RuntimeException("sin orden"));
        doThrow(new RuntimeException("aviso")).when(avisoMock).avisar(5L);

        assertDoesNotThrow(() -> servicio.registrarOrdenSiAprobada(5L, "APROBADA"));

        verify(avisoMock).registrarSeguimiento(5L, "sin orden");
    }

    @Test
    void siElSeguimientoFallaElAvisoYaSeIntento() {
        OrdenCompraService ordenes = org.mockito.Mockito.mock(OrdenCompraService.class);
        AvisoOrdenNoCreada avisoMock = org.mockito.Mockito.mock(AvisoOrdenNoCreada.class);
        CotizacionService servicio = servicio(ordenes, avisoMock);
        when(ordenes.crearAutomatica(5L)).thenThrow(new RuntimeException("sin orden"));
        doThrow(new RuntimeException("historial")).when(avisoMock).registrarSeguimiento(5L, "sin orden");

        assertDoesNotThrow(() -> servicio.registrarOrdenSiAprobada(5L, "APROBADA"));

        verify(avisoMock).avisar(5L);
    }

    private static CotizacionService servicio(OrdenCompraService ordenes, AvisoOrdenNoCreada avisoDeOrden) {
        return new CotizacionService(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, ordenes, null, null, avisoDeOrden);
    }

    private static Cotizacion cotizacion() {
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Mina Sur");
        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setIdEstadoCotizacion(4);
        estado.setCodigoEstado("APROBADA");
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(7L);
        cotizacion.setCodigoCotizacion("COT-7");
        cotizacion.setCliente(cliente);
        cotizacion.setEstadoCotizacion(estado);
        return cotizacion;
    }
}
