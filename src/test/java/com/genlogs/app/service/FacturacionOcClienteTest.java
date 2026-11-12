package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.genlogs.app.dto.DetalleFacturacionRequest;
import com.genlogs.app.dto.FacturacionRequest;
import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.EstadoFacturacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.TipoComprobante;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.DetalleFacturacionRepository;
import com.genlogs.app.repository.EstadoFacturacionRepository;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.TipoComprobanteRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.pdf.PdfGenerationService;
import com.genlogs.app.service.sunat.SunatService;

@ExtendWith(MockitoExtension.class)
class FacturacionOcClienteTest {

    @Mock private FacturacionRepository facturacionRepository;
    @Mock private DetalleFacturacionRepository detalleFacturacionRepository;
    @Mock private OrdenCompraRepository ordenCompraRepository;
    @Mock private TipoComprobanteRepository tipoComprobanteRepository;
    @Mock private EstadoFacturacionRepository estadoFacturacionRepository;
    @Mock private CondicionPagoRepository condicionPagoRepository;
    @Mock private MonedaRepository monedaRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PdfGenerationService pdfGenerationService;
    @Mock private TasaIgvService tasaIgvService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private SunatService sunatService;
    @Mock private CotizacionDetalleRepository cotizacionDetalleRepository;
    @Mock private MailService mailService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private FacturacionService servicio;

    @BeforeEach
    void usuario() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "x", List.of()));
    }

    @AfterEach
    void limpia() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void alRegistrarDevuelveElNumeroDeLaMinera() {
        OrdenCompra orden = orden("4500123");
        when(ordenCompraRepository.findById(3L)).thenReturn(Optional.of(orden));
        when(tipoComprobanteRepository.findById(1)).thenReturn(Optional.of(tipo()));
        when(condicionPagoRepository.findById(1)).thenReturn(Optional.of(condicion()));
        when(monedaRepository.findById(1)).thenReturn(Optional.of(moneda()));
        when(facturacionRepository.findBySerieComprobanteAndNumeroComprobante("F001", "00000012"))
                .thenReturn(Optional.empty());
        when(estadoFacturacionRepository.findByCodigoEstado("EMITIDA")).thenReturn(Optional.of(estado()));
        Usuario ana = new Usuario();
        ana.setNombreUsuario("ana");
        when(usuarioRepository.findByNombreUsuario("ana")).thenReturn(Optional.of(ana));
        when(tasaIgvService.porcentajeVigente(any())).thenReturn(new BigDecimal("18.00"));
        when(facturacionRepository.save(any())).thenAnswer(invocacion -> {
            Facturacion guardada = invocacion.getArgument(0);
            guardada.setIdFacturacion(8L);
            return guardada;
        });
        when(productoRepository.findById(4L)).thenReturn(Optional.of(new Producto()));
        when(detalleFacturacionRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(detalleFacturacionRepository.findByFacturacion(8L)).thenReturn(List.of());

        FacturacionResponse respuesta = servicio.crear(pedido(3L));

        assertEquals("4500123", respuesta.getNumeroOcCliente());
        assertEquals("OC-1", respuesta.getNumeroOrdenCompra());
    }

    @Test
    void alConsultarDevuelveElNumeroCuandoLaOrdenLoTrae() {
        when(facturacionRepository.findById(8L)).thenReturn(Optional.of(comprobante(orden("4500123"))));
        when(detalleFacturacionRepository.findByFacturacion(8L)).thenReturn(List.of());

        FacturacionResponse respuesta = servicio.buscarPorId(8L);

        assertEquals("4500123", respuesta.getNumeroOcCliente());
        assertEquals("OC-1", respuesta.getNumeroOrdenCompra());
    }

    @Test
    void alConsultarDevuelveNullCuandoLaOrdenNoLoTiene() {
        when(facturacionRepository.findById(8L)).thenReturn(Optional.of(comprobante(orden(null))));
        when(detalleFacturacionRepository.findByFacturacion(8L)).thenReturn(List.of());

        FacturacionResponse respuesta = servicio.buscarPorId(8L);

        assertNull(respuesta.getNumeroOcCliente());
        assertEquals("OC-1", respuesta.getNumeroOrdenCompra());
    }

    private static FacturacionRequest pedido(Long idOrden) {
        DetalleFacturacionRequest linea = new DetalleFacturacionRequest();
        linea.setIdProducto(4L);
        linea.setCantidad(BigDecimal.ONE);
        linea.setPrecioUnitario(new BigDecimal("100.00"));
        FacturacionRequest pedido = new FacturacionRequest();
        pedido.setIdOrdenCompra(idOrden);
        pedido.setIdTipoComprobante(1);
        pedido.setIdCondicionPago(1);
        pedido.setIdMoneda(1);
        pedido.setSerieComprobante("F001");
        pedido.setNumeroComprobante("00000012");
        pedido.setFechaEmision(LocalDate.of(2026, 11, 12));
        pedido.setFechaVencimiento(LocalDate.of(2026, 12, 12));
        pedido.setLineas(List.of(linea));
        return pedido;
    }

    private static OrdenCompra orden(String numeroOcCliente) {
        OrdenCompra orden = new OrdenCompra();
        orden.setIdOrdenCompra(3L);
        orden.setNumeroOrdenCompra("OC-1");
        orden.setNumeroOcCliente(numeroOcCliente);
        return orden;
    }

    private static Facturacion comprobante(OrdenCompra orden) {
        Facturacion factura = new Facturacion();
        factura.setIdFacturacion(8L);
        factura.setOrdenCompra(orden);
        factura.setSerieComprobante("F001");
        factura.setNumeroComprobante("00000012");
        factura.setEstadoFacturacion(estado());
        factura.setTipoComprobante(tipo());
        factura.setPorcentajeIgv(new BigDecimal("18.00"));
        factura.setMontoPagado(BigDecimal.ZERO);
        return factura;
    }

    private static TipoComprobante tipo() {
        TipoComprobante tipo = new TipoComprobante();
        tipo.setNombreTipo("Factura");
        return tipo;
    }

    private static CondicionPago condicion() {
        CondicionPago condicion = new CondicionPago();
        condicion.setDiasCredito((short) 0);
        return condicion;
    }

    private static Moneda moneda() {
        Moneda moneda = new Moneda();
        moneda.setCodigoMoneda("PEN");
        return moneda;
    }

    private static EstadoFacturacion estado() {
        EstadoFacturacion estado = new EstadoFacturacion();
        estado.setCodigoEstado("EMITIDA");
        estado.setNombreEstado("Emitida");
        return estado;
    }
}
