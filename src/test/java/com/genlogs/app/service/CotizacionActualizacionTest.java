package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.genlogs.app.dto.CambiarEstadoCotizacionRequest;
import com.genlogs.app.dto.CotizacionDetalleRequest;
import com.genlogs.app.dto.CotizacionRequest;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.ContactoTercero;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.SeguimientoCotizacion;
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
import com.genlogs.app.model.TipoCambio;
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.pdf.PdfGenerationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class CotizacionActualizacionTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 1);
    private static final BigDecimal TIPO_CAMBIO = new BigDecimal("3.7520");

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
    @Mock private TipoCambioRepository tiposCambio;

    private CotizacionService servicio;

    @BeforeEach
    void servicio() {
        servicio = new CotizacionService(
                cotizaciones, detalles, estados, seguimientos, pdf, adjuntos, clientes, contactos, usuarios,
                monedas, condiciones, sectores, productos, servicios, unidades, cloudinary, mail, tasaIgv,
                tipoCambioCotizacion, avisoAltoValor, ordenes, transacciones, entityManager, avisoOrdenNoCreada,
                tiposCambio);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "x", List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enviadaANegociacionDejaSeguimiento() {
        sesion();
        Cotizacion cotizacion = cotizacion("ENVIADA", false);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(cotizaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estados.findByCodigoEstado("EN_NEGOCIACION")).thenReturn(Optional.of(estado("EN_NEGOCIACION", 3, false)));
        when(seguimientos.findByCotizacion(15L)).thenReturn(List.of());
        when(detalles.findByCotizacion(15L)).thenReturn(List.of());
        when(adjuntos.findByCotizacion(15L)).thenReturn(List.of());

        CambiarEstadoCotizacionRequest pedido = new CambiarEstadoCotizacionRequest();
        pedido.setCodigoEstado("EN_NEGOCIACION");
        pedido.setComentario("El cliente pidió cambios");

        servicio.cambiarEstado(15L, pedido);

        assertEquals("EN_NEGOCIACION", cotizacion.getEstadoCotizacion().getCodigoEstado());
        ArgumentCaptor<SeguimientoCotizacion> seguimiento = ArgumentCaptor.forClass(SeguimientoCotizacion.class);
        verify(seguimientos).save(seguimiento.capture());
        assertEquals("El cliente pidió cambios", seguimiento.getValue().getComentario());
    }

    @Test
    void anuladaConMotivoDejaElMotivoYSinMotivoSeRechaza() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));

        CambiarEstadoCotizacionRequest vacio = new CambiarEstadoCotizacionRequest();
        vacio.setCodigoEstado("ANULADA");
        vacio.setComentario("   ");
        BusinessException sinMotivo = assertThrows(BusinessException.class, () -> servicio.cambiarEstado(15L, vacio));
        assertEquals(ReglasAprobacionCotizacion.MOTIVO_ANULACION_OBLIGATORIO, sinMotivo.getMessage());

        sesion();
        when(cotizaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estados.findByCodigoEstado("ANULADA")).thenReturn(Optional.of(estado("ANULADA", 7, true)));
        when(seguimientos.findByCotizacion(15L)).thenReturn(List.of());
        when(detalles.findByCotizacion(15L)).thenReturn(List.of());
        when(adjuntos.findByCotizacion(15L)).thenReturn(List.of());
        CambiarEstadoCotizacionRequest pedido = new CambiarEstadoCotizacionRequest();
        pedido.setCodigoEstado("ANULADA");
        pedido.setComentario("El cliente desistió");

        servicio.cambiarEstado(15L, pedido);

        ArgumentCaptor<SeguimientoCotizacion> seguimiento = ArgumentCaptor.forClass(SeguimientoCotizacion.class);
        verify(seguimientos).save(seguimiento.capture());
        assertEquals("El cliente desistió", seguimiento.getValue().getComentario());
    }

    @Test
    void anuladaDesdeUnEstadoFinalSeRechaza() {
        Cotizacion cotizacion = cotizacion("RECHAZADA", true);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(estados.findByCodigoEstado("ANULADA")).thenReturn(Optional.of(estado("ANULADA", 7, true)));
        CambiarEstadoCotizacionRequest pedido = new CambiarEstadoCotizacionRequest();
        pedido.setCodigoEstado("ANULADA");
        pedido.setComentario("Tarpe");

        BusinessException error = assertThrows(BusinessException.class, () -> servicio.cambiarEstado(15L, pedido));

        assertEquals("No se puede cambiar el estado de una cotización ya finalizada", error.getMessage());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void putEnBorradorConservaIdYCodigoYReemplazaLineas() {
        sesion();
        catalogo();
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        cotizacion.setCodigoCotizacion("COT-15");
        cotizacion.setFechaCotizacion(FECHA);
        cotizacion.setFechaValidez(FECHA.plusDays(15));
        cotizacion.setTipoCambioUsado(TIPO_CAMBIO);
        CotizacionDetalle vieja = new CotizacionDetalle();
        vieja.setIdCotizacionDetalle(9L);
        vieja.setDescripcionPersonalizada("Vieja");
        AtomicInteger lecturas = new AtomicInteger();
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(cotizaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detalles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detalles.findByCotizacion(15L)).thenAnswer(inv -> lecturas.getAndIncrement() == 0
                ? List.of(vieja) : List.of());
        when(seguimientos.findByCotizacion(15L)).thenReturn(List.of());
        when(adjuntos.findByCotizacion(15L)).thenReturn(List.of());

        CotizacionResponse respuesta = servicio.actualizar(15L, pedido());

        assertEquals(15L, respuesta.getIdCotizacion());
        assertEquals("COT-15", respuesta.getCodigoCotizacion());
        assertEquals(FECHA, cotizacion.getFechaCotizacion());
        assertEquals(0, TIPO_CAMBIO.compareTo(cotizacion.getTipoCambioUsado()));
        assertEquals("I", vieja.getStatus());
        ArgumentCaptor<CotizacionDetalle> lineas = ArgumentCaptor.forClass(CotizacionDetalle.class);
        verify(detalles, org.mockito.Mockito.atLeastOnce()).save(lineas.capture());
        assertEquals("Filtro nuevo", lineas.getAllValues().get(lineas.getAllValues().size() - 1)
                .getDescripcionPersonalizada());
        verify(cotizaciones, never()).save(org.mockito.ArgumentMatchers.argThat(
                guardada -> guardada != cotizacion));
    }

    @Test
    void putConservaElCambioGuardado() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        listo(cotizacion);
        CotizacionRequest pedido = pedido();
        pedido.setTipoCambio(TIPO_CAMBIO);
        pedido.setFechaTipoCambio(FECHA);

        servicio.actualizar(15L, pedido);

        assertEquals(0, TIPO_CAMBIO.compareTo(cotizacion.getTipoCambioUsado()));
        verify(tiposCambio, never()).findByMoneda_CodigoMonedaAndFecha(any(), any());
    }

    @Test
    void putConCambioDistintoSeRechaza() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        when(clientes.findById(1L)).thenReturn(Optional.of(cliente));
        Moneda sol = new Moneda();
        sol.setCodigoMoneda("PEN");
        when(monedas.findById(1)).thenReturn(Optional.of(sol));
        when(condiciones.findById(1)).thenReturn(Optional.of(new CondicionPago()));
        CotizacionRequest pedido = pedido();
        pedido.setTipoCambio(new BigDecimal("9.9999"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> servicio.actualizar(15L, pedido));

        assertEquals("El tipo de cambio de una cotización guardada no se modifica", error.getMessage());
        assertEquals(0, TIPO_CAMBIO.compareTo(cotizacion.getTipoCambioUsado()));
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void putConGuardadoNullYCambioValidoLoGuarda() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        cotizacion.setTipoCambioUsado(null);
        Moneda dolar = new Moneda();
        dolar.setIdMoneda(2);
        dolar.setCodigoMoneda("USD");
        listo(cotizacion);
        when(monedas.findById(2)).thenReturn(Optional.of(dolar));
        TipoCambio fila = new TipoCambio();
        fila.setValor(new BigDecimal("3.7100"));
        when(tiposCambio.findByMoneda_CodigoMonedaAndFecha("USD", FECHA)).thenReturn(Optional.of(fila));
        CotizacionRequest pedido = pedido();
        pedido.setIdMoneda(2);
        pedido.setTipoCambio(new BigDecimal("3.7100"));
        pedido.setFechaTipoCambio(FECHA);

        servicio.actualizar(15L, pedido);

        assertEquals(0, new BigDecimal("3.7100").compareTo(cotizacion.getTipoCambioUsado()));
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void borradorNoPasaARechazadaNiVencida() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));

        for (String destino : List.of("RECHAZADA", "VENCIDA")) {
            CambiarEstadoCotizacionRequest pedido = new CambiarEstadoCotizacionRequest();
            pedido.setCodigoEstado(destino);
            pedido.setComentario("motivo");
            BusinessException error = assertThrows(BusinessException.class,
                    () -> servicio.cambiarEstado(15L, pedido));
            assertEquals(ReglasAprobacionCotizacion.DESTINO_NO_PERMITIDO, error.getMessage());
        }

        assertEquals("BORRADOR", cotizacion.getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, never()).save(any());
        verify(seguimientos, never()).save(any());
    }

    @Test
    void putFueraDeBorradorSeRechaza() {
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion("PENDIENTE_APROBACION", false)));
        BusinessException pendiente = assertThrows(BusinessException.class,
                () -> servicio.actualizar(15L, pedido()));
        assertEquals("Solo se actualiza una cotización en borrador", pendiente.getMessage());

        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion("ENVIADA", false)));
        assertThrows(BusinessException.class, () -> servicio.actualizar(15L, pedido()));
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void putConEurNoGuarda() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setIdTercero(8L);
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        when(clientes.findById(1L)).thenReturn(Optional.of(cliente));
        Moneda euro = new Moneda();
        euro.setIdMoneda(3);
        euro.setCodigoMoneda("EUR");
        when(monedas.findById(3)).thenReturn(Optional.of(euro));
        CotizacionRequest pedido = pedido();
        pedido.setIdMoneda(3);

        BusinessException error = assertThrows(BusinessException.class, () -> servicio.actualizar(15L, pedido));

        assertEquals("La cotización solo admite soles o dólares.", error.getMessage());
        assertEquals(0, TIPO_CAMBIO.compareTo(cotizacion.getTipoCambioUsado()));
        verify(cotizaciones, never()).save(any());
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void putCambiaElContactoDelMismoCliente() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        cotizacion.setContacto(contacto(4L, 8L));
        listo(cotizacion);
        when(contactos.findById(5L)).thenReturn(Optional.of(contacto(5L, 8L)));
        CotizacionRequest pedido = pedido();
        pedido.setIdContacto(5L);

        CotizacionResponse respuesta = servicio.actualizar(15L, pedido);

        assertEquals(5L, cotizacion.getContacto().getIdContacto());
        assertEquals(5L, respuesta.getIdContacto());
        assertEquals(15L, respuesta.getIdCotizacion());
        assertEquals("COT-15", respuesta.getCodigoCotizacion());
    }

    @Test
    void putConContactoDeOtroClienteNoGuarda() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        ContactoTercero anterior = contacto(4L, 8L);
        cotizacion.setContacto(anterior);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setIdTercero(8L);
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        when(clientes.findById(1L)).thenReturn(Optional.of(cliente));
        when(contactos.findById(5L)).thenReturn(Optional.of(contacto(5L, 99L)));
        CotizacionRequest pedido = pedido();
        pedido.setIdContacto(5L);

        BusinessException error = assertThrows(BusinessException.class, () -> servicio.actualizar(15L, pedido));

        assertEquals("El contacto no pertenece a este cliente", error.getMessage());
        assertEquals(anterior, cotizacion.getContacto());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void putSinIdContactoDejaElContactoEnNull() {
        Cotizacion cotizacion = cotizacion("BORRADOR", false);
        cotizacion.setContacto(contacto(4L, 8L));
        listo(cotizacion);

        CotizacionResponse respuesta = servicio.actualizar(15L, pedido());

        assertEquals(null, cotizacion.getContacto());
        assertEquals(null, respuesta.getIdContacto());
    }

    private static ContactoTercero contacto(Long idContacto, Long idTercero) {
        Tercero tercero = new Tercero();
        tercero.setIdTercero(idTercero);
        ContactoTercero contacto = new ContactoTercero();
        contacto.setIdContacto(idContacto);
        contacto.setTercero(tercero);
        return contacto;
    }

    private void listo(Cotizacion cotizacion) {
        sesion();
        catalogo();
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(cotizaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detalles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detalles.findByCotizacion(15L)).thenReturn(List.of());
        when(seguimientos.findByCotizacion(15L)).thenReturn(List.of());
        when(adjuntos.findByCotizacion(15L)).thenReturn(List.of());
    }

    private void sesion() {
        Usuario ana = new Usuario();
        ana.setNombreUsuario("ana");
        when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(ana));
        Query auditoria = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(auditoria);
        when(auditoria.setParameter(anyString(), any())).thenReturn(auditoria);
        when(auditoria.getSingleResult()).thenReturn(1);
    }

    private void catalogo() {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setIdTercero(8L);
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        SectorEconomico sector = new SectorEconomico();
        sector.setIdSectorEconomico(1);
        cliente.setSectorEconomico(sector);
        when(clientes.findById(1L)).thenReturn(Optional.of(cliente));

        Moneda sol = new Moneda();
        sol.setIdMoneda(1);
        sol.setCodigoMoneda("PEN");
        lenient().when(monedas.findById(1)).thenReturn(Optional.of(sol));
        when(condiciones.findById(1)).thenReturn(Optional.of(new CondicionPago()));

        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        when(unidades.findFirstByStatusOrderByIdUnidadMedidaAsc("A")).thenReturn(Optional.of(unidad));
    }

    private static Cotizacion cotizacion(String codigo, boolean fin) {
        EstadoCotizacion estado = estado(codigo, 1, fin);
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        Moneda sol = new Moneda();
        sol.setCodigoMoneda("PEN");
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(15L);
        cotizacion.setCodigoCotizacion("COT-15");
        cotizacion.setCliente(cliente);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setMoneda(sol);
        cotizacion.setFechaCotizacion(FECHA);
        cotizacion.setFechaValidez(FECHA.plusDays(15));
        cotizacion.setTipoCambioUsado(TIPO_CAMBIO);
        return cotizacion;
    }

    private static EstadoCotizacion estado(String codigo, int id, boolean fin) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setIdEstadoCotizacion(id);
        estado.setCodigoEstado(codigo);
        estado.setNombreEstado(codigo);
        estado.setEsFinal(fin);
        return estado;
    }

    private static CotizacionRequest pedido() {
        CotizacionDetalleRequest linea = new CotizacionDetalleRequest();
        linea.setDescripcion("Filtro nuevo");
        linea.setCantidad(new BigDecimal("3"));
        linea.setPrecioUnitario(new BigDecimal("20"));
        CotizacionRequest pedido = new CotizacionRequest();
        pedido.setIdCliente(1L);
        pedido.setIdMoneda(1);
        pedido.setIdCondicionPago(1);
        pedido.setObservaciones("Ajustada");
        pedido.setLineas(List.of(linea));
        return pedido;
    }
}
