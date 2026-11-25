package com.genlogs.app.pagos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.ResultadoPagoResponse;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.pagos.PagoEnLineaRegistro.Estado;
import com.genlogs.app.pagos.PagoEnLineaRegistro.Resultado;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.NotificacionService;

@ExtendWith(MockitoExtension.class)
class PagoEnLineaServiceTest {

    @Mock private MercadoPagoCliente mercadoPago;
    @Mock private PagoEnLineaRegistro registro;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionService notificacionService;

    private PagoEnLineaService servicio;

    @BeforeEach
    void setUp() {
        servicio = new PagoEnLineaService(mercadoPago, registro, usuarioRepository, notificacionService);
        servicio.setSecretoWebhook("secreto");
        Cliente cliente = new Cliente();
        cliente.setIdCliente(5L);
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("mina");
        usuario.setCliente(cliente);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mina", "n/a", List.of()));
        lenient().when(usuarioRepository.findWithClienteByNombreUsuario("mina")).thenReturn(Optional.of(usuario));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void iniciarCobraElSaldoEnSolesConLaReferenciaDelComprobante() {
        when(mercadoPago.habilitado()).thenReturn(true);
        when(registro.datosParaPagar(1L, 5L)).thenReturn(new PagoEnLineaRegistro.DatosPago(1L, "F001-00000001", new BigDecimal("100.00")));
        when(mercadoPago.crearPreferencia(any())).thenReturn(new MercadoPagoCliente.Preferencia("pref-1", "https://mp/checkout"));

        assertEquals("https://mp/checkout", servicio.iniciar(1L).urlPago());

        ArgumentCaptor<MercadoPagoCliente.PedidoPreferencia> pedido = ArgumentCaptor.forClass(MercadoPagoCliente.PedidoPreferencia.class);
        verify(mercadoPago).crearPreferencia(pedido.capture());
        assertEquals("FAC-1", pedido.getValue().referenciaExterna());
        assertEquals(new BigDecimal("100.00"), pedido.getValue().monto());
        assertEquals("PEN", pedido.getValue().moneda());
    }

    @Test
    void sinCredencialNoSeIniciaElPago() {
        when(mercadoPago.habilitado()).thenReturn(false);

        assertThrows(BusinessException.class, () -> servicio.iniciar(1L));
        verify(mercadoPago, never()).crearPreferencia(any());
    }

    @Test
    void alVolverSeConsultaElPagoYSeAvisaAlJefeYAlContador() {
        MercadoPagoCliente.Pago pago = new MercadoPagoCliente.Pago("777", "approved", "FAC-1", new BigDecimal("118.00"), "PEN", null);
        when(mercadoPago.consultarPago("777")).thenReturn(pago);
        when(registro.registrar(pago, 5L)).thenReturn(
                new Resultado(Estado.REGISTRADO, 1L, "F001-00000001", new BigDecimal("118.00"), BigDecimal.ZERO, null));

        ResultadoPagoResponse r = servicio.confirmarDesdePortal("777");

        assertEquals("REGISTRADO", r.estado());
        assertTrue(r.mensaje().contains("F001-00000001"));
        verify(notificacionService).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.PAGO_EN_LINEA),
                anyString(), anyString(), anyString(), eq(false));
        verify(notificacionService).avisarRol(eq(RolSistema.CONTADOR_GENERAL), eq(TipoAviso.PAGO_EN_LINEA),
                anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void siElRegresoYElAvisoLleganJuntosNoSeCobraDosVeces() {
        MercadoPagoCliente.Pago pago = new MercadoPagoCliente.Pago("777", "approved", "FAC-1", new BigDecimal("118.00"), "PEN", null);
        when(mercadoPago.consultarPago("777")).thenReturn(pago);
        when(registro.registrar(pago, 5L)).thenThrow(new DataIntegrityViolationException("uq_pago_en_linea_externo"));

        assertEquals("YA_REGISTRADO", servicio.confirmarDesdePortal("777").estado());
        verify(notificacionService, never()).avisarRol(any(), any(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void unAvisoConFirmaInvalidaNoConsultaNiRegistra() {
        assertFalse(servicio.recibirAviso("ts=1,v1=cualquiera", "req", "777", "payment"));
        verify(mercadoPago, never()).consultarPago(anyString());
    }

    @Test
    void unAvisoFirmadoConsultaElPagoYLoRegistraSinSesion() {
        String v1 = FirmaMercadoPago.hmacHex("id:777;request-id:req;ts:1;", "secreto");
        MercadoPagoCliente.Pago pago = new MercadoPagoCliente.Pago("777", "approved", "FAC-1", new BigDecimal("118.00"), "PEN", null);
        when(mercadoPago.consultarPago("777")).thenReturn(pago);
        when(registro.registrar(eq(pago), isNull())).thenReturn(
                new Resultado(Estado.REVISION, 1L, "F001-00000001", new BigDecimal("118.00"), new BigDecimal("18.00"), "supera el saldo"));

        assertTrue(servicio.recibirAviso("ts=1,v1=" + v1, "req", "777", "payment"));
        verify(notificacionService, times(1)).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.PAGO_EN_LINEA),
                eq("Pago en línea por revisar"), anyString(), anyString(), eq(true));
    }

    @Test
    void unAvisoQueNoEsDePagosSeIgnora() {
        assertTrue(servicio.recibirAviso(null, null, "1", "merchant_order"));
        verify(mercadoPago, never()).consultarPago(anyString());
    }
}