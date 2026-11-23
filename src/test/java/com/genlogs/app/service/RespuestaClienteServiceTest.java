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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.RespuestaClienteRequest;
import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RespuestaClienteServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private CotizacionRepository cotizaciones;
    @Mock private EstadoCotizacionRepository estados;
    @Mock private SeguimientoCotizacionRepository seguimientos;
    @Mock private AdjuntoCotizacionRepository adjuntos;
    @Mock private CotizacionService cotizacionService;

    private RespuestaClienteService servicio;
    private final LocalDate hoy = LocalDate.now(PlazoDespacho.LIMA);

    @BeforeEach
    void setUp() {
        servicio = new RespuestaClienteService(
                cotizaciones, adjuntos, seguimientos, usuarios, estados, null, cotizacionService);
        Usuario jefe = new Usuario();
        jefe.setNombreUsuario("jefe");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jefe", "n/a", List.of(new SimpleGrantedAuthority("ROLE_JEFE_ADMINISTRATIVO"))));
        lenient().when(usuarios.findByNombreUsuario("jefe")).thenReturn(Optional.of(jefe));
        lenient().when(usuarios.findByNombreUsuario("vendedor")).thenReturn(Optional.of(jefe));
        lenient().when(estados.findByCodigoEstado(any())).thenAnswer(inv -> Optional.of(estado(inv.getArgument(0))));
        lenient().when(seguimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cotizaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pidioCambiosQuedaEnNegociacionYNoSeCierra() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));
        when(adjuntos.findById(7L)).thenReturn(Optional.of(adjunto(7L, enviada)));

        assertEquals("EN_NEGOCIACION", servicio.registrar(15L,
                pedido("EN_NEGOCIACION", "Pidió otro plazo", 7L)));

        assertEquals("EN_NEGOCIACION", enviada.getEstadoCotizacion().getCodigoEstado());
        SeguimientoCotizacion seguimiento = seguimientoGuardado();
        assertEquals("ENVIADA", seguimiento.getEstadoAnterior().getCodigoEstado());
        assertEquals("EN_NEGOCIACION", seguimiento.getEstadoCotizacion().getCodigoEstado());
        assertEquals(hoy, seguimiento.getFechaRespuesta());
        assertEquals("whatsapp", seguimiento.getMedio());
        assertEquals("Pidió otro plazo", seguimiento.getComentario());
        assertEquals(7L, seguimiento.getAdjunto().getIdAdjunto());

        Cotizacion yaNegociando = cotizacion(16L, "EN_NEGOCIACION");
        when(cotizaciones.findById(16L)).thenReturn(Optional.of(yaNegociando));

        assertEquals("EN_NEGOCIACION", servicio.registrar(16L,
                pedido("EN_NEGOCIACION", "Sigue pidiendo cambios", null)));
        assertEquals("EN_NEGOCIACION", yaNegociando.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void aceptarGuardaLaAceptacionSinCrearLaOrden() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));
        when(adjuntos.findById(7L)).thenReturn(Optional.of(adjunto(7L, enviada)));

        assertEquals("ACEPTADA_CLIENTE", servicio.registrar(15L,
                pedido("ACEPTADA_CLIENTE", "Aceptó por correo", 7L)));

        assertEquals("ACEPTADA_CLIENTE", enviada.getEstadoCotizacion().getCodigoEstado());
        SeguimientoCotizacion seguimiento = seguimientoGuardado();
        assertEquals("ENVIADA", seguimiento.getEstadoAnterior().getCodigoEstado());
        assertEquals("ACEPTADA_CLIENTE", seguimiento.getEstadoCotizacion().getCodigoEstado());
        assertEquals("Aceptó por correo", seguimiento.getComentario());
        assertEquals(hoy, seguimiento.getFechaRespuesta());
        assertEquals("whatsapp", seguimiento.getMedio());
        assertEquals(7L, seguimiento.getAdjunto().getIdAdjunto());
        assertEquals("https://cdn.example/correo.pdf", seguimiento.getRutaAdjunto());
        verify(cotizacionService, never()).registrarOrdenSiAprobada(any(), any());
    }

    @Test
    void rechazarGuardaElRechazo() {
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));

        assertEquals("RECHAZADA", servicio.registrar(15L,
                pedido("RECHAZADA", "No compran este año", null)));

        assertEquals("RECHAZADA", enviada.getEstadoCotizacion().getCodigoEstado());
        SeguimientoCotizacion seguimiento = seguimientoGuardado();
        assertEquals("ENVIADA", seguimiento.getEstadoAnterior().getCodigoEstado());
        assertEquals("RECHAZADA", seguimiento.getEstadoCotizacion().getCodigoEstado());
        assertEquals("No compran este año", seguimiento.getComentario());
    }

    @Test
    void elAsistenteLogisticoRegistraYOtroRolRecibe403() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "vendedor", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ASISTENTE_LOGISTICO"))));
        Cotizacion enviada = cotizacion(15L, "ENVIADA");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(enviada));

        assertEquals("EN_NEGOCIACION", servicio.registrar(15L,
                pedido("EN_NEGOCIACION", "Pidió descuento", null)));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", "n/a", List.of(new SimpleGrantedAuthority("ROLE_GERENTE_GENERAL"))));
        AccessDeniedException error = assertThrows(AccessDeniedException.class,
                () -> servicio.registrar(15L, pedido("RECHAZADA", "No", null)));
        assertEquals(RespuestaClienteService.SOLO_EL_JEFE, error.getMessage());
    }

    @Test
    void noVuelveAEscribirAprobadaYUnaViejaSeLeeComoAceptada() {
        assertEquals("ACEPTADA_CLIENTE", CodigoEstadoCotizacion.visible("APROBADA"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.registrar(15L, pedido("APROBADA", "Aceptó", null)));
        assertEquals(RespuestaClienteService.DESTINO_NO_ADMITIDO, error.getMessage());
        verify(cotizaciones, never()).save(any());

        Cotizacion vieja = cotizacion(20L, "APROBADA");
        when(cotizaciones.findById(20L)).thenReturn(Optional.of(vieja));
        assertThrows(EstadoNoPermitidoException.class,
                () -> servicio.registrar(20L, pedido("RECHAZADA", "Ya no", null)));
        assertEquals("APROBADA", vieja.getEstadoCotizacion().getCodigoEstado());
    }

    private SeguimientoCotizacion seguimientoGuardado() {
        ArgumentCaptor<SeguimientoCotizacion> guardado = ArgumentCaptor.forClass(SeguimientoCotizacion.class);
        verify(seguimientos).save(guardado.capture());
        return guardado.getValue();
    }

    private RespuestaClienteRequest pedido(String codigo, String comentario, Long idAdjunto) {
        RespuestaClienteRequest request = new RespuestaClienteRequest();
        request.setCodigoEstado(codigo);
        request.setComentario(comentario);
        request.setIdAdjunto(idAdjunto);
        request.setFechaRespuesta(hoy.toString());
        request.setMedio("whatsapp");
        return request;
    }

    private static Cotizacion cotizacion(Long id, String codigo) {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(id);
        cotizacion.setEstadoCotizacion(estado(codigo));
        cotizacion.setFechaValidez(LocalDate.now(PlazoDespacho.LIMA).plusDays(5));
        cotizacion.setStatus("A");
        return cotizacion;
    }

    private static EstadoCotizacion estado(String codigo) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(codigo);
        estado.setNombreEstado(codigo);
        return estado;
    }

    private static AdjuntoCotizacion adjunto(Long id, Cotizacion cotizacion) {
        AdjuntoCotizacion adjunto = new AdjuntoCotizacion();
        adjunto.setIdAdjunto(id);
        adjunto.setCotizacion(cotizacion);
        adjunto.setStatus("A");
        adjunto.setNombreArchivo("correo.pdf");
        adjunto.setUrlArchivo("https://cdn.example/correo.pdf");
        return adjunto;
    }
}
