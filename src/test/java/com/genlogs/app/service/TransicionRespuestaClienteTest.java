package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.EnviarCotizacionRequest;
import com.genlogs.app.dto.EnviarCotizacionResponse;
import com.genlogs.app.dto.SeguimientoCotizacionResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class TransicionRespuestaClienteTest {

    @Mock private CotizacionRepository cotizaciones;
    @Mock private AdjuntoCotizacionRepository adjuntos;
    @Mock private SeguimientoCotizacionRepository seguimientos;
    @Mock private UsuarioRepository usuarios;
    @Mock private EstadoCotizacionRepository estados;
    @Mock private CloudinaryService cloudinary;
    @Mock private CotizacionService envios;

    private RespuestaClienteService servicio;
    private final LocalDate hoy = LocalDate.now(PlazoDespacho.LIMA);

    @BeforeEach
    void setUp() {
        servicio = new RespuestaClienteService(
                cotizaciones, adjuntos, seguimientos, usuarios, estados, cloudinary, envios);
        lenient().when(estados.findByCodigoEstado(anyString())).thenAnswer(inv -> {
            EstadoCotizacion estado = new EstadoCotizacion();
            estado.setIdEstadoCotizacion(1);
            estado.setCodigoEstado(inv.getArgument(0));
            estado.setNombreEstado(inv.getArgument(0));
            return Optional.of(estado);
        });
        lenient().when(seguimientos.findByCotizacion(any())).thenReturn(List.of());
        lenient().when(seguimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cotizaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void marcarEnviadaReutilizaElEnvioYNoCambiaElEstadoSiFalla() {
        entrar("vendedor", "ROLE_ASISTENTE_LOGISTICO");
        Cotizacion cotizacion = cotizacion(4L, "APROBADA_INTERNA", "Aprobada por el Jefe");
        cotizacion.setCodigoCotizacion("COT-001");
        cotizacion.setCliente(cliente("compras@mina.pe"));
        when(cotizaciones.findById(4L)).thenReturn(Optional.of(cotizacion));
        when(envios.enviar(eq(4L), any())).thenReturn(
                EnviarCotizacionResponse.builder().success(true).mensaje("Cotización enviada a compras@mina.pe").build());

        servicio.marcarEnviada(4L);

        ArgumentCaptor<EnviarCotizacionRequest> envio = ArgumentCaptor.forClass(EnviarCotizacionRequest.class);
        verify(envios).enviar(eq(4L), envio.capture());
        assertEquals("compras@mina.pe", envio.getValue().getCorreoDestinatario());
        assertEquals("Cotización COT-001", envio.getValue().getAsunto());
        assertEquals("", envio.getValue().getMensaje());
        assertEquals(true, envio.getValue().isIncluirDetalles());
        assertEquals("APROBADA_INTERNA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, never()).save(any());
        verify(seguimientos, never()).save(any());

        when(envios.enviar(eq(4L), any())).thenReturn(EnviarCotizacionResponse.builder()
                .success(false)
                .mensaje("No se pudo enviar el correo. Intenta de nuevo o revisa la configuración de correo.")
                .build());
        BusinessException fallo = assertThrows(BusinessException.class, () -> servicio.marcarEnviada(4L));
        assertEquals("No se pudo enviar el correo. Intenta de nuevo o revisa la configuración de correo.", fallo.getMessage());
        assertEquals("APROBADA_INTERNA", cotizacion.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void sinCorreoValidoResponde400YSigueAprobadaInterna() {
        entrar("jefe", "ROLE_JEFE_ADMINISTRATIVO");
        Cotizacion sinCorreo = cotizacion(4L, "APROBADA_INTERNA", "Aprobada por el Jefe");
        sinCorreo.setCliente(cliente("  "));
        when(cotizaciones.findById(4L)).thenReturn(Optional.of(sinCorreo));

        BusinessException vacio = assertThrows(BusinessException.class, () -> servicio.marcarEnviada(4L));
        assertEquals(RespuestaClienteService.SIN_CORREO, vacio.getMessage());

        Cotizacion invalido = cotizacion(4L, "APROBADA_INTERNA", "Aprobada por el Jefe");
        invalido.setCliente(cliente("no-es-correo"));
        when(cotizaciones.findById(4L)).thenReturn(Optional.of(invalido));
        BusinessException mal = assertThrows(BusinessException.class, () -> servicio.marcarEnviada(4L));
        assertEquals(RespuestaClienteService.SIN_CORREO, mal.getMessage());
        assertEquals("APROBADA_INTERNA", invalido.getEstadoCotizacion().getCodigoEstado());
        verify(envios, never()).enviar(any(), any());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void otraTransicionAlMarcarEnviadaResponde409() {
        entrar("jefe", "ROLE_JEFE_ADMINISTRATIVO");
        for (String origen : new String[] {"BORRADOR", "ENVIADA", "EN_NEGOCIACION", "ACEPTADA_CLIENTE"}) {
            Cotizacion cotizacion = cotizacion(4L, origen, origen);
            when(cotizaciones.findById(4L)).thenReturn(Optional.of(cotizacion));
            EstadoNoPermitidoException error = assertThrows(EstadoNoPermitidoException.class,
                    () -> servicio.marcarEnviada(4L));
            assertEquals(true, error.getMessage().contains("aprobada por el Jefe"));
            assertEquals(origen, cotizacion.getEstadoCotizacion().getCodigoEstado());
        }
        verify(seguimientos, never()).save(any());
    }

    @Test
    void enviadaONegociacionAceptanPidenCambiosORechazan() {
        entrar("jefe", "ROLE_JEFE_ADMINISTRATIVO");

        Cotizacion enviada = cotizacion(1L, "ENVIADA", "Enviada");
        when(cotizaciones.findById(1L)).thenReturn(Optional.of(enviada));
        SeguimientoCotizacionResponse aceptada = servicio.registrarRespuesta(
                1L, "aceptó", hoy.toString(), "WhatsApp", "Confirmó por mensaje", null);
        assertEquals("ACEPTADA_CLIENTE", enviada.getEstadoCotizacion().getCodigoEstado());
        assertEquals("ENVIADA", aceptada.getEstadoAnterior());
        assertEquals("ACEPTADA_CLIENTE", aceptada.getEstadoNuevo());
        assertEquals(hoy, aceptada.getFechaRespuesta());
        assertEquals("whatsapp", aceptada.getMedio());
        assertEquals("Confirmó por mensaje", aceptada.getComentario());
        assertEquals("jefe", aceptada.getUsuario());
        assertNull(aceptada.getIdAdjunto());
        verify(envios, never()).registrarOrdenSiAprobada(anyLong(), any());

        Cotizacion negociacion = cotizacion(2L, "EN_NEGOCIACION", "En negociación");
        when(cotizaciones.findById(2L)).thenReturn(Optional.of(negociacion));
        servicio.registrarRespuesta(2L, "pidió_cambios", hoy.toString(), "correo", "Falta el plazo", null);
        assertEquals("EN_NEGOCIACION", negociacion.getEstadoCotizacion().getCodigoEstado());

        Cotizacion enviadaOtra = cotizacion(3L, "ENVIADA", "Enviada");
        when(cotizaciones.findById(3L)).thenReturn(Optional.of(enviadaOtra));
        servicio.registrarRespuesta(3L, "pidió cambios", hoy.toString(), "llamada", "Quiere otro precio", null);
        assertEquals("EN_NEGOCIACION", enviadaOtra.getEstadoCotizacion().getCodigoEstado());

        Cotizacion paraRechazo = cotizacion(5L, "EN_NEGOCIACION", "En negociación");
        when(cotizaciones.findById(5L)).thenReturn(Optional.of(paraRechazo));
        SeguimientoCotizacionResponse rechazo = servicio.registrarRespuesta(
                5L, "rechazó", hoy.toString(), "otro", "No hay presupuesto", null);
        assertEquals("RECHAZADA", paraRechazo.getEstadoCotizacion().getCodigoEstado());
        assertEquals("EN_NEGOCIACION", rechazo.getEstadoAnterior());
        assertEquals("RECHAZADA", rechazo.getEstadoNuevo());
        assertEquals("otro", rechazo.getMedio());
    }

    @Test
    void aceptarSoloConAdjuntoTambienVale() {
        entrar("vendedor", "ROLE_ASISTENTE_LOGISTICO");
        Cotizacion enviada = cotizacion(8L, "ENVIADA", "Enviada");
        when(cotizaciones.findById(8L)).thenReturn(Optional.of(enviada));
        when(cloudinary.subirDocumento(any(), anyString())).thenReturn("https://cdn.example/oc.pdf");
        MockMultipartFile pdf = new MockMultipartFile(
                "adjunto", "../oc minera.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        SeguimientoCotizacionResponse respuesta = servicio.registrarRespuesta(
                8L, "aceptó", hoy.toString(), "correo", "  ", pdf);

        assertEquals("ACEPTADA_CLIENTE", enviada.getEstadoCotizacion().getCodigoEstado());
        assertEquals("https://cdn.example/oc.pdf", respuesta.getRutaAdjunto());
        assertNull(respuesta.getComentario());
        assertEquals("oc_minera.pdf", capturarAdjunto());
        verify(envios, never()).registrarOrdenSiAprobada(anyLong(), any());
    }

    @Test
    void transicionesQueNoEstanEnLaListaResponden409() {
        entrar("jefe", "ROLE_JEFE_ADMINISTRATIVO");
        for (String origen : new String[] {"BORRADOR", "APROBADA_INTERNA", "RECHAZADA", "ACEPTADA_CLIENTE"}) {
            Cotizacion cotizacion = cotizacion(9L, origen, origen);
            when(cotizaciones.findById(9L)).thenReturn(Optional.of(cotizacion));
            assertThrows(EstadoNoPermitidoException.class, () -> servicio.registrarRespuesta(
                    9L, "aceptó", hoy.toString(), "correo", "ok", null));
            assertThrows(EstadoNoPermitidoException.class, () -> servicio.registrarRespuesta(
                    9L, "rechazó", hoy.toString(), "correo", "no", null));
            assertEquals(origen, cotizacion.getEstadoCotizacion().getCodigoEstado());
        }
    }

    @Test
    void aceptoSinEvidenciaCambiosORechazoSinMotivoYFechaFuturaNoGuardan() {
        entrar("jefe", "ROLE_JEFE_ADMINISTRATIVO");

        assertEquals(RespuestaClienteService.EVIDENCIA_ACEPTACION, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "aceptó", hoy.toString(), "correo", " ", null)).getMessage());
        assertEquals(RespuestaClienteService.MOTIVO_CAMBIOS, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "pidió_cambios", hoy.toString(), "correo", "", null)).getMessage());
        assertEquals(RespuestaClienteService.MOTIVO_RECHAZO, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "rechazó", hoy.toString(), "llamada", null, null)).getMessage());
        assertEquals(RespuestaClienteService.FECHA_FUTURA, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(
                        9L, "rechazó", hoy.plusDays(1).toString(), "llamada", "no", null)).getMessage());
        assertEquals(RespuestaClienteService.FECHA_OBLIGATORIA, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "rechazó", " ", "llamada", "no", null)).getMessage());
        assertEquals(RespuestaClienteService.MEDIO_INVALIDO, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "rechazó", hoy.toString(), "telegram", "no", null)).getMessage());
        assertEquals(RespuestaClienteService.ACCION_INVALIDA, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "tal vez", hoy.toString(), "correo", "no", null)).getMessage());
        verify(cotizaciones, never()).findById(any());
        verify(seguimientos, never()).save(any());
    }

    @Test
    void adjuntoQueNoEsPdfPngOJpgOPesaDeMasSeRechaza() {
        entrar("jefe", "ROLE_JEFE_ADMINISTRATIVO");
        Cotizacion enviada = cotizacion(9L, "ENVIADA", "Enviada");
        when(cotizaciones.findById(9L)).thenReturn(Optional.of(enviada));
        MockMultipartFile texto = new MockMultipartFile(
                "adjunto", "nota.txt", "text/plain", "hola".getBytes());
        assertEquals(AdjuntoEvidencia.TIPO_NO_PERMITIDO, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "aceptó", hoy.toString(), "correo", null, texto)).getMessage());

        byte[] grande = new byte[] { '%', 'P', 'D', 'F' };
        MockMultipartFile pesado = new MockMultipartFile("adjunto", "oc.pdf", "application/pdf", grande) {
            @Override
            public long getSize() {
                return AdjuntoEvidencia.MAX_BYTES + 1;
            }
        };
        assertEquals(AdjuntoEvidencia.DEMASIADO_GRANDE, assertThrows(BusinessException.class,
                () -> servicio.registrarRespuesta(9L, "aceptó", hoy.toString(), "correo", null, pesado)).getMessage());
        verify(cloudinary, never()).subirDocumento(any(), anyString());
        assertEquals("ENVIADA", enviada.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void gerenteYClienteReciben403() {
        Cotizacion enviada = cotizacion(9L, "ENVIADA", "Enviada");
        for (String rol : new String[] {"ROLE_GERENTE_GENERAL", "ROLE_CLIENTE", "ROLE_CONTADOR_GENERAL"}) {
            entrar("otro", rol);
            assertThrows(AccessDeniedException.class, () -> servicio.marcarEnviada(9L));
            assertThrows(AccessDeniedException.class, () -> servicio.registrarRespuesta(
                    9L, "aceptó", hoy.toString(), "correo", "ok", null));
        }
        verify(cotizaciones, never()).findById(any());
        assertEquals("ENVIADA", enviada.getEstadoCotizacion().getCodigoEstado());
    }

    private String capturarAdjunto() {
        ArgumentCaptor<com.genlogs.app.model.AdjuntoCotizacion> captor =
                ArgumentCaptor.forClass(com.genlogs.app.model.AdjuntoCotizacion.class);
        verify(adjuntos).save(captor.capture());
        return captor.getValue().getNombreArchivo();
    }

    private void entrar(String usuario, String rol) {
        Usuario persona = new Usuario();
        persona.setNombreUsuario(usuario);
        lenient().when(usuarios.findByNombreUsuario(usuario)).thenReturn(Optional.of(persona));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, "n/a", List.of(new SimpleGrantedAuthority(rol))));
    }

    private static Cotizacion cotizacion(Long id, String codigo, String nombre) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setIdEstadoCotizacion(codigo.hashCode());
        estado.setCodigoEstado(codigo);
        estado.setNombreEstado(nombre);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(id);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setStatus("A");
        return cotizacion;
    }

    private static Cliente cliente(String correo) {
        Tercero tercero = new Tercero();
        tercero.setCorreo(correo);
        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        return cliente;
    }
}
