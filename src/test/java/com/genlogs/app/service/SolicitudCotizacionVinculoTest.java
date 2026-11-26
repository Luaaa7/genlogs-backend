package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.gestion.SolicitudInternaResponse;
import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.SolicitudEventoRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * De la solicitud a la cotización: la cotización se liga a la solicitud (id_cotizacion) y, cuando el
 * Jefe la aprueba, la solicitud pasa a COTIZADA y queda escrito en la bitácora.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudCotizacionVinculoTest {

    @Mock private SolicitudCotizacionRepository solicitudes;
    @Mock private SolicitudEventoRepository eventos;
    @Mock private UsuarioRepository usuarios;
    @Mock private PlazoSolicitud plazo;
    @Mock private CalendarioHabil calendario;
    @Mock private BitacoraSolicitud bitacora;
    @Mock private CotizacionRepository cotizaciones;

    private GestionSolicitudService servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        servicio = new GestionSolicitudService(solicitudes, eventos, usuarios, plazo, calendario, bitacora,
                cotizaciones);
        ana = new Usuario();
        ana.setIdUsuario(7L);
        ana.setNombreUsuario("ana");
        org.mockito.Mockito.lenient().when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(ana));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "x", List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void vincularPonlaCotizacionEnLaSolicitudYDejaBitacora() {
        SolicitudCotizacion solicitud = solicitud(1L, "EN_REVISION", 10L);
        Cotizacion cotizacion = cotizacion(50L, "COT-2026-50", 10L);
        when(solicitudes.findById(1L)).thenReturn(Optional.of(solicitud));
        when(cotizaciones.findById(50L)).thenReturn(Optional.of(cotizacion));

        SolicitudInternaResponse respuesta = servicio.vincularCotizacion(1L, 50L);

        assertSame(cotizacion, solicitud.getCotizacion());
        assertEquals("EN_REVISION", solicitud.getEstadoSolicitud());
        assertEquals(50L, respuesta.idCotizacion());
        assertEquals("COT-2026-50", respuesta.codigoCotizacion());
        verify(solicitudes).save(solicitud);
        verify(bitacora).registrar(eq(1L), eq(7L), eq(TipoEventoSolicitud.ESTADO), contains("COT-2026-50"));
    }

    @Test
    void vincularUnaPendienteLaPasaAEnRevisionYSeLaAsignaAQuienCotiza() {
        SolicitudCotizacion solicitud = solicitud(1L, "PENDIENTE", 10L);
        when(solicitudes.findById(1L)).thenReturn(Optional.of(solicitud));
        when(cotizaciones.findById(50L)).thenReturn(Optional.of(cotizacion(50L, "COT-2026-50", 10L)));

        servicio.vincularCotizacion(1L, 50L);

        assertEquals("EN_REVISION", solicitud.getEstadoSolicitud());
        assertSame(ana, solicitud.getUsuarioAsignado());
    }

    @Test
    void vincularConUnaCotizacionDeOtroClienteSeRechaza() {
        SolicitudCotizacion solicitud = solicitud(1L, "EN_REVISION", 10L);
        when(solicitudes.findById(1L)).thenReturn(Optional.of(solicitud));
        when(cotizaciones.findById(50L)).thenReturn(Optional.of(cotizacion(50L, "COT-2026-50", 99L)));

        assertThrows(BusinessException.class, () -> servicio.vincularCotizacion(1L, 50L));

        assertNull(solicitud.getCotizacion());
        verify(solicitudes, never()).save(any());
    }

    @Test
    void vincularUnaSolicitudQueYaTieneCotizacionSeRechaza() {
        SolicitudCotizacion solicitud = solicitud(1L, "EN_REVISION", 10L);
        solicitud.setCotizacion(cotizacion(40L, "COT-2026-40", 10L));
        when(solicitudes.findById(1L)).thenReturn(Optional.of(solicitud));
        when(cotizaciones.findById(50L)).thenReturn(Optional.of(cotizacion(50L, "COT-2026-50", 10L)));

        assertThrows(BusinessException.class, () -> servicio.vincularCotizacion(1L, 50L));

        assertEquals(40L, solicitud.getCotizacion().getIdCotizacion());
    }

    @Test
    void vincularUnaSolicitudYaCotizadaDa409() {
        when(solicitudes.findById(1L)).thenReturn(Optional.of(solicitud(1L, "COTIZADA", 10L)));

        assertThrows(EstadoNoPermitidoException.class, () -> servicio.vincularCotizacion(1L, 50L));
    }

    @Test
    void alAprobarLaCotizacionLaSolicitudPasaACotizada() {
        SolicitudCotizacion solicitud = solicitud(1L, "EN_REVISION", 10L);
        solicitud.setCotizacion(cotizacion(50L, "COT-2026-50", 10L));
        when(solicitudes.findFirstByCotizacion_IdCotizacionAndStatus(50L, "A")).thenReturn(Optional.of(solicitud));

        servicio.marcarCotizadaPorCotizacion(50L);

        assertEquals("COTIZADA", solicitud.getEstadoSolicitud());
        verify(solicitudes).save(solicitud);
        verify(bitacora).registrar(eq(1L), eq(7L), eq(TipoEventoSolicitud.ESTADO), contains("COTIZADA"));
    }

    @Test
    void siLaCotizacionNoVienePorUnaSolicitudNoPasaNada() {
        when(solicitudes.findFirstByCotizacion_IdCotizacionAndStatus(50L, "A")).thenReturn(Optional.empty());

        servicio.marcarCotizadaPorCotizacion(50L);

        verify(solicitudes, never()).save(any());
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void siLaSolicitudYaEstabaCotizadaNoSeEscribeDeNuevo() {
        SolicitudCotizacion solicitud = solicitud(1L, "COTIZADA", 10L);
        when(solicitudes.findFirstByCotizacion_IdCotizacionAndStatus(50L, "A")).thenReturn(Optional.of(solicitud));

        servicio.marcarCotizadaPorCotizacion(50L);

        verify(solicitudes, never()).save(any());
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void detalleDevuelveLaSolicitudConSuCotizacion() {
        SolicitudCotizacion solicitud = solicitud(1L, "EN_REVISION", 10L);
        solicitud.setCotizacion(cotizacion(50L, "COT-2026-50", 10L));
        when(solicitudes.findById(1L)).thenReturn(Optional.of(solicitud));

        SolicitudInternaResponse respuesta = servicio.detalle(1L);

        assertEquals("SOL-2026-1", respuesta.codigoSolicitud());
        assertEquals(50L, respuesta.idCotizacion());
        assertEquals(10L, respuesta.idCliente());
    }

    @Test
    void detalleDeUnaSolicitudQueNoExisteDa404() {
        when(solicitudes.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.detalle(9L));
    }

    // ---------------------------------------------------------------- apoyo

    private static SolicitudCotizacion solicitud(Long id, String estado, Long idCliente) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(idCliente);
        SolicitudCotizacion s = new SolicitudCotizacion();
        s.setIdSolicitud(id);
        s.setCodigoSolicitud("SOL-2026-" + id);
        s.setAsunto("Filtros");
        s.setCliente(cliente);
        s.setEstadoSolicitud(estado);
        s.setStatus("A");
        return s;
    }

    private static Cotizacion cotizacion(Long id, String codigo, Long idCliente) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(idCliente);
        Cotizacion c = new Cotizacion();
        c.setIdCotizacion(id);
        c.setCodigoCotizacion(codigo);
        c.setCliente(cliente);
        c.setStatus("A");
        return c;
    }
}
