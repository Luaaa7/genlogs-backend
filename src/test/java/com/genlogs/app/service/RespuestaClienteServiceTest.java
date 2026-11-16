package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.RespuestaClienteRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RespuestaClienteServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private CotizacionRepository cotizaciones;
    @Mock private CotizacionDetalleRepository detalles;
    @Mock private EstadoCotizacionRepository estados;
    @Mock private SeguimientoCotizacionRepository seguimientos;
    @Mock private OrdenCompraRepository ordenes;
    @Mock private AdjuntoCotizacionRepository adjuntos;

    private RespuestaClienteService servicio;
    private Usuario jefe;

    @BeforeEach
    void setUp() {
        PortalCotizacionService portal = new PortalCotizacionService(
                usuarios, cotizaciones, detalles, estados, seguimientos, ordenes);
        servicio = new RespuestaClienteService(cotizaciones, adjuntos, usuarios, portal);
        jefe = new Usuario();
        jefe.setNombreUsuario("jefe");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jefe", "n/a", List.of(new SimpleGrantedAuthority("ROLE_JEFE_ADMINISTRATIVO"))));
        lenient().when(usuarios.findByNombreUsuario("jefe")).thenReturn(Optional.of(jefe));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elJefeApruebaDesdeEnviadaYDesdeNegociacion() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));
        when(adjuntos.findById(7L)).thenReturn(Optional.of(adjunto(7L, enviada)));
        when(estados.findByCodigoEstado("APROBADA")).thenReturn(Optional.of(estado("APROBADA")));

        assertEquals("APROBADA", servicio.registrar(15L, pedido("APROBADA", "Llamó el 16/11 a las 9:00", 7L)));
        assertEquals("APROBADA", enviada.getEstadoCotizacion().getCodigoEstado());

        BusinessException segunda = assertThrows(BusinessException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "Llamó el 16/11 a las 9:00", 7L)));
        assertEquals("Esta cotización ya no se puede aprobar ni rechazar", segunda.getMessage());
        verify(cotizaciones, org.mockito.Mockito.times(1)).save(any());

        Cotizacion negociacion = cotizacion(16L, "EN_NEGOCIACION");
        when(cotizaciones.findById(16L)).thenReturn(Optional.of(negociacion));
        when(adjuntos.findById(8L)).thenReturn(Optional.of(adjunto(8L, negociacion)));

        assertEquals("APROBADA", servicio.registrar(16L, pedido("APROBADA", "Correo del 16/11 10:15", 8L)));
        assertEquals("APROBADA", negociacion.getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void elRechazoNoDejaLaCotizacionAprobada() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));
        when(adjuntos.findById(7L)).thenReturn(Optional.of(adjunto(7L, enviada)));
        when(estados.findByCodigoEstado("RECHAZADA")).thenReturn(Optional.of(estado("RECHAZADA")));

        assertEquals("RECHAZADA", servicio.registrar(15L, pedido("RECHAZADA", "Correo del 16/11: no compran", 7L)));

        assertEquals("RECHAZADA", enviada.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void sinComentarioOSinAdjuntoNoGuarda() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));

        BusinessException sinComentario = assertThrows(BusinessException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "  ", 7L)));
        assertEquals(RespuestaClienteService.COMENTARIO_OBLIGATORIO, sinComentario.getMessage());

        BusinessException sinAdjunto = assertThrows(BusinessException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "Llamó el 16/11 a las 9:00", null)));
        assertEquals(RespuestaClienteService.ADJUNTO_OBLIGATORIO, sinAdjunto.getMessage());
        assertEquals("ENVIADA", enviada.getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void fueraDeVigenciaSeRechazaIgualQueEnElPortal() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        enviada.setFechaValidez(LocalDate.now(PlazoDespacho.LIMA).minusDays(1));
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));
        when(adjuntos.findById(7L)).thenReturn(Optional.of(adjunto(7L, enviada)));

        BusinessException vencida = assertThrows(BusinessException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "Llamó el 16/11 a las 9:00", 7L)));

        assertEquals("La cotización está vencida", vencida.getMessage());
        assertEquals("ENVIADA", enviada.getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void otroRolNoRegistra() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ASISTENTE_LOGISTICO"))));

        AccessDeniedException error = assertThrows(AccessDeniedException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "Llamó el 16/11 a las 9:00", 7L)));

        assertEquals(RespuestaClienteService.SOLO_EL_JEFE, error.getMessage());
        verify(cotizaciones, never()).findById(any());
    }

    @Test
    void unEstadoQueNoEsEnviadaNiNegociacionSeRechaza() {
        Cotizacion borrador = cotizacion(15L, "BORRADOR");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(borrador));
        when(adjuntos.findById(7L)).thenReturn(Optional.of(adjunto(7L, borrador)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "Llamó el 16/11 a las 9:00", 7L)));

        assertEquals("Esta cotización ya no se puede aprobar ni rechazar", error.getMessage());
        assertEquals("BORRADOR", borrador.getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, never()).save(any());
    }

    private static RespuestaClienteRequest pedido(String codigo, String comentario, Long idAdjunto) {
        RespuestaClienteRequest request = new RespuestaClienteRequest();
        request.setCodigoEstado(codigo);
        request.setComentario(comentario);
        request.setIdAdjunto(idAdjunto);
        return request;
    }

    private static Cotizacion cotizacion(Long id, String codigo) {
        EstadoCotizacion estado = estado(codigo);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(id);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setFechaValidez(LocalDate.now(PlazoDespacho.LIMA).plusDays(5));
        cotizacion.setStatus("A");
        return cotizacion;
    }

    private static EstadoCotizacion estado(String codigo) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(codigo);
        return estado;
    }

    private static AdjuntoCotizacion adjunto(Long id, Cotizacion cotizacion) {
        AdjuntoCotizacion adjunto = new AdjuntoCotizacion();
        adjunto.setIdAdjunto(id);
        adjunto.setCotizacion(cotizacion);
        adjunto.setStatus("A");
        return adjunto;
    }
}
