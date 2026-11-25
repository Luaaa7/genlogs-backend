package com.genlogs.app.pagos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.dto.PortalComprobanteResponse;
import com.genlogs.app.dto.RegistrarPagoRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoFacturacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.PagoEnLinea;
import com.genlogs.app.pagos.PagoEnLineaRegistro.Estado;
import com.genlogs.app.pagos.PagoEnLineaRegistro.Resultado;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.PagoEnLineaRepository;
import com.genlogs.app.service.FacturacionService;

@ExtendWith(MockitoExtension.class)
class PagoEnLineaRegistroTest {

    private static final Long MI_CLIENTE = 5L;

    @Mock private FacturacionRepository facturacionRepository;
    @Mock private FacturacionService facturacionService;
    @Mock private PagoEnLineaRepository pagoEnLineaRepository;

    private PagoEnLineaRegistro registro;

    @BeforeEach
    void setUp() {
        registro = new PagoEnLineaRegistro(facturacionRepository, facturacionService, pagoEnLineaRepository);
        lenient().when(facturacionService.totalDe(any())).thenReturn(new BigDecimal("118.00"));
    }

    @Test
    void listaSoloLosDelClienteYDiceSiSePuedenPagar() {
        Facturacion soles = factura(1L, MI_CLIENTE, "EMITIDA", "PEN", "0.00");
        Facturacion dolares = factura(2L, MI_CLIENTE, "EMITIDA", "USD", "0.00");
        Facturacion pagada = factura(3L, MI_CLIENTE, "PAGADA", "PEN", "118.00");
        when(facturacionRepository.findByCliente(MI_CLIENTE)).thenReturn(List.of(soles, dolares, pagada));

        List<PortalComprobanteResponse> lista = registro.misComprobantes(MI_CLIENTE, true);

        assertEquals(3, lista.size());
        assertTrue(lista.get(0).isPuedePagarEnLinea());
        assertEquals(new BigDecimal("118.00"), lista.get(0).getSaldoPendiente());
        assertFalse(lista.get(1).isPuedePagarEnLinea());
        assertTrue(lista.get(1).getMotivoSinPago().contains("soles"));
        assertFalse(lista.get(2).isPuedePagarEnLinea());
        assertEquals("El comprobante ya está pagado.", lista.get(2).getMotivoSinPago());
    }

    @Test
    void sinCredencialNingunoSePuedePagar() {
        when(facturacionRepository.findByCliente(MI_CLIENTE))
                .thenReturn(List.of(factura(1L, MI_CLIENTE, "EMITIDA", "PEN", "0.00")));

        PortalComprobanteResponse c = registro.misComprobantes(MI_CLIENTE, false).get(0);

        assertFalse(c.isPuedePagarEnLinea());
        assertEquals("El pago en línea todavía no está habilitado.", c.getMotivoSinPago());
    }

    @Test
    void cobraElSaldoPendienteYNoDejaPagarAnuladaDolaresNiDeOtraEmpresa() {
        when(facturacionRepository.findById(1L)).thenReturn(Optional.of(factura(1L, MI_CLIENTE, "PARCIAL", "PEN", "18.00")));
        when(facturacionRepository.findById(2L)).thenReturn(Optional.of(factura(2L, MI_CLIENTE, "ANULADA", "PEN", "0.00")));
        when(facturacionRepository.findById(3L)).thenReturn(Optional.of(factura(3L, MI_CLIENTE, "EMITIDA", "USD", "0.00")));
        when(facturacionRepository.findById(4L)).thenReturn(Optional.of(factura(4L, 9L, "EMITIDA", "PEN", "0.00")));

        PagoEnLineaRegistro.DatosPago datos = registro.datosParaPagar(1L, MI_CLIENTE);
        assertEquals(new BigDecimal("100.00"), datos.saldo());
        assertEquals("F001-00000001", datos.comprobante());

        assertEquals("El comprobante está anulado.",
                assertThrows(BusinessException.class, () -> registro.datosParaPagar(2L, MI_CLIENTE)).getMessage());
        assertThrows(BusinessException.class, () -> registro.datosParaPagar(3L, MI_CLIENTE));
        assertThrows(ResourceNotFoundException.class, () -> registro.datosParaPagar(4L, MI_CLIENTE));
    }

    @Test
    void unPagoAprobadoRegistraElCobroUnaSolaVez() {
        Facturacion f = factura(1L, MI_CLIENTE, "EMITIDA", "PEN", "0.00");
        when(facturacionRepository.findById(1L)).thenReturn(Optional.of(f));
        when(pagoEnLineaRepository.existsByProveedorAndIdPagoExterno("MERCADO_PAGO", "777")).thenReturn(false, true);
        when(facturacionService.registrarPagoDelSistema(eq(1L), any(), eq("mercadopago"), eq("PAGO_EN_LINEA")))
                .thenReturn(FacturacionResponse.builder().saldoPendiente(BigDecimal.ZERO).build());

        Resultado primero = registro.registrar(pago("777", "approved", "FAC-1", "118.00", "PEN"), MI_CLIENTE);
        Resultado segundo = registro.registrar(pago("777", "approved", "FAC-1", "118.00", "PEN"), null);

        assertEquals(Estado.REGISTRADO, primero.estado());
        assertEquals(0, BigDecimal.ZERO.compareTo(primero.saldoPendiente()));
        assertEquals(Estado.YA_REGISTRADO, segundo.estado());

        ArgumentCaptor<RegistrarPagoRequest> pedido = ArgumentCaptor.forClass(RegistrarPagoRequest.class);
        verify(facturacionService).registrarPagoDelSistema(eq(1L), pedido.capture(), eq("mercadopago"), eq("PAGO_EN_LINEA"));
        assertEquals(new BigDecimal("118.00"), pedido.getValue().getMontoPagado());
        assertEquals("Mercado Pago", pedido.getValue().getMedioPago());
        assertEquals("777", pedido.getValue().getNumeroOperacion());

        ArgumentCaptor<PagoEnLinea> guardado = ArgumentCaptor.forClass(PagoEnLinea.class);
        verify(pagoEnLineaRepository).saveAndFlush(guardado.capture());
        assertEquals("777", guardado.getValue().getIdPagoExterno());
        assertEquals("MERCADO_PAGO", guardado.getValue().getProveedor());
        assertEquals("mercadopago", guardado.getValue().getUserCreate());
    }

    @Test
    void pendienteORechazadoNoTocanElSaldo() {
        when(facturacionRepository.findById(1L)).thenReturn(Optional.of(factura(1L, MI_CLIENTE, "EMITIDA", "PEN", "0.00")));

        assertEquals(Estado.PENDIENTE, registro.registrar(pago("8", "in_process", "FAC-1", "118.00", "PEN"), MI_CLIENTE).estado());
        assertEquals(Estado.RECHAZADO, registro.registrar(pago("9", "rejected", "FAC-1", "118.00", "PEN"), MI_CLIENTE).estado());
        verify(facturacionService, never()).registrarPagoDelSistema(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void masQueElSaldoOEnOtraMonedaQuedaParaRevision() {
        when(facturacionRepository.findById(1L)).thenReturn(Optional.of(factura(1L, MI_CLIENTE, "PARCIAL", "PEN", "100.00")));

        assertEquals(Estado.REVISION, registro.registrar(pago("10", "approved", "FAC-1", "118.00", "PEN"), null).estado());
        assertEquals(Estado.REVISION, registro.registrar(pago("11", "approved", "FAC-1", "18.00", "USD"), null).estado());
        verify(facturacionService, never()).registrarPagoDelSistema(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void unPagoDeOtraEmpresaDesdeElPortalResponde404YUnaReferenciaAjenaSeIgnora() {
        when(facturacionRepository.findById(4L)).thenReturn(Optional.of(factura(4L, 9L, "EMITIDA", "PEN", "0.00")));

        assertThrows(ResourceNotFoundException.class,
                () -> registro.registrar(pago("12", "approved", "FAC-4", "118.00", "PEN"), MI_CLIENTE));
        assertEquals(Estado.IGNORADO, registro.registrar(pago("13", "approved", "OTRA-1", "1.00", "PEN"), null).estado());
        assertNull(PagoEnLineaRegistro.idDeReferencia("FAC-abc"));
        assertEquals(12L, PagoEnLineaRegistro.idDeReferencia(PagoEnLineaRegistro.referencia(12L)));
    }

    // ------------------------------------------------------------------------

    private static MercadoPagoCliente.Pago pago(String id, String estado, String referencia, String monto, String moneda) {
        return new MercadoPagoCliente.Pago(id, estado, referencia, new BigDecimal(monto), moneda,
                LocalDateTime.of(2026, 11, 24, 10, 0));
    }

    private static Facturacion factura(Long id, Long idCliente, String estado, String moneda, String pagado) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(idCliente);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCliente(cliente);
        OrdenCompra orden = new OrdenCompra();
        orden.setCotizacion(cotizacion);
        EstadoFacturacion e = new EstadoFacturacion();
        e.setCodigoEstado(estado);
        e.setNombreEstado(estado);
        Moneda m = new Moneda();
        m.setCodigoMoneda(moneda);
        Facturacion f = new Facturacion();
        f.setIdFacturacion(id);
        f.setOrdenCompra(orden);
        f.setEstadoFacturacion(e);
        f.setMoneda(m);
        f.setSerieComprobante("F001");
        f.setNumeroComprobante(String.format("%08d", id));
        f.setMontoPagado(new BigDecimal(pagado));
        f.setStatus("A");
        return f;
    }
}