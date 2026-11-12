package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.genlogs.app.dto.CotizacionDetalleRequest;
import com.genlogs.app.dto.CotizacionRequest;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.ContactoTerceroRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.pdf.PdfGenerationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class CotizacionAltaTipoCambioTest {

    private static final BigDecimal VIGENTE = new BigDecimal("3.7520");

    @Mock private CotizacionRepository cotizaciones;
    @Mock private CotizacionDetalleRepository detalles;
    @Mock private EstadoCotizacionRepository estados;
    @Mock private SeguimientoCotizacionRepository seguimientos;
    @Mock private PdfGenerationService pdf;
    @Mock private AdjuntoCotizacionRepository adjuntos;
    @Mock private ClienteRepository clientes;
    @Mock private ContactoTerceroRepository contactos;
    @Mock private UsuarioRepository usuarios;
    @Mock private MonedaRepository monedas;
    @Mock private CondicionPagoRepository condiciones;
    @Mock private SectorEconomicoRepository sectores;
    @Mock private ProductoRepository productos;
    @Mock private ServicioRepository servicios;
    @Mock private UnidadMedidaRepository unidades;
    @Mock private CloudinaryService cloudinary;
    @Mock private MailService mail;
    @Mock private TasaIgvService tasaIgv;
    @Mock private TipoCambioCotizacion tipoCambioCotizacion;
    @Mock private AvisoAltoValorCotizacion avisoAltoValor;
    @Mock private OrdenCompraService ordenes;
    @Mock private TransactionTemplate transacciones;
    @Mock private EntityManager entityManager;
    @Mock private AvisoOrdenNoCreada avisoOrdenNoCreada;

    private CotizacionService servicio;
    private final AtomicReference<Cotizacion> guardada = new AtomicReference<>();

    @BeforeEach
    void datos() {
        servicio = new CotizacionService(
                cotizaciones, detalles, estados, seguimientos, pdf, adjuntos, clientes, contactos, usuarios,
                monedas, condiciones, sectores, productos, servicios, unidades, cloudinary, mail, tasaIgv,
                tipoCambioCotizacion, avisoAltoValor, ordenes, transacciones, entityManager, avisoOrdenNoCreada);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "x", List.of()));

        Usuario ana = new Usuario();
        ana.setNombreUsuario("ana");
        when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(ana));

        Query auditoria = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(auditoria);
        when(auditoria.setParameter(anyString(), any())).thenReturn(auditoria);
        when(auditoria.getSingleResult()).thenReturn(1);

        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        when(clientes.findById(1L)).thenReturn(Optional.of(cliente));

        Moneda sol = new Moneda();
        sol.setIdMoneda(1);
        sol.setCodigoMoneda("PEN");
        when(monedas.findById(1)).thenReturn(Optional.of(sol));

        when(condiciones.findById(1)).thenReturn(Optional.of(new CondicionPago()));

        EstadoCotizacion borrador = new EstadoCotizacion();
        borrador.setIdEstadoCotizacion(1);
        borrador.setCodigoEstado("BORRADOR");
        borrador.setNombreEstado("Borrador");
        when(estados.findByCodigoEstado("BORRADOR")).thenReturn(Optional.of(borrador));

        when(tasaIgv.porcentajeVigente()).thenReturn(new BigDecimal("18.00"));
        when(tipoCambioCotizacion.vigente()).thenReturn(VIGENTE);
        when(unidades.findFirstByStatusOrderByIdUnidadMedidaAsc("A")).thenReturn(Optional.of(new UnidadMedida()));

        when(cotizaciones.save(any())).thenAnswer(invocacion -> {
            Cotizacion cotizacion = invocacion.getArgument(0);
            if (cotizacion.getIdCotizacion() == null) {
                cotizacion.setIdCotizacion(15L);
            }
            guardada.set(cotizacion);
            return cotizacion;
        });
        when(cotizaciones.findCodigoCotizacionGenerado(15L)).thenReturn(Optional.of("COT-15"));
        when(cotizaciones.findById(15L)).thenAnswer(invocacion -> Optional.of(guardada.get()));
        when(cotizaciones.findTotalesByCotizacion(15L)).thenReturn(Optional.empty());
        when(detalles.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(detalles.findByCotizacion(15L)).thenReturn(List.of());
        when(seguimientos.findByCotizacion(15L)).thenReturn(List.of());
        when(adjuntos.findByCotizacion(15L)).thenReturn(List.of());
    }

    @AfterEach
    void limpia() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elAltaGuardaElTipoDeCambioVigenteYElDetalleLoDevuelve() {
        CotizacionResponse creada = servicio.crear(pedido());

        ArgumentCaptor<Cotizacion> captor = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizaciones).save(captor.capture());
        assertEquals(0, VIGENTE.compareTo(captor.getValue().getTipoCambioUsado()));
        assertEquals(0, captor.getValue().getTipoCambioUsado().compareTo(creada.getTipoCambioUsado()));

        CotizacionResponse detalle = servicio.buscarPorId(15L);

        assertEquals(0, captor.getValue().getTipoCambioUsado().compareTo(detalle.getTipoCambioUsado()));
    }

    private static CotizacionRequest pedido() {
        CotizacionDetalleRequest linea = new CotizacionDetalleRequest();
        linea.setDescripcionPersonalizada("Filtro");
        linea.setCantidad(BigDecimal.ONE);
        linea.setPrecioUnitario(new BigDecimal("10.00"));
        CotizacionRequest pedido = new CotizacionRequest();
        pedido.setIdCliente(1L);
        pedido.setIdMoneda(1);
        pedido.setIdCondicionPago(1);
        pedido.setLineas(List.of(linea));
        return pedido;
    }
}
