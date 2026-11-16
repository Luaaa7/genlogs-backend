package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.genlogs.app.dto.CotizacionDetalleRequest;
import com.genlogs.app.dto.CotizacionRequest;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.EnviarCotizacionRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoCambio;
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
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.pdf.PdfGenerationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    @Mock private TipoCambioRepository tiposCambio;

    private CotizacionService servicio;
    private final AtomicReference<Cotizacion> guardada = new AtomicReference<>();

    @BeforeEach
    void datos() {
        servicio = new CotizacionService(
                cotizaciones, detalles, estados, seguimientos, pdf, adjuntos, clientes, contactos, usuarios,
                monedas, condiciones, sectores, productos, servicios, unidades, cloudinary, mail, tasaIgv,
                tipoCambioCotizacion, avisoAltoValor, ordenes, transacciones, entityManager, avisoOrdenNoCreada,
                tiposCambio);

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

        when(tasaIgv.porcentajeVigente(any(LocalDate.class))).thenReturn(new BigDecimal("18.00"));
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
        when(cotizaciones.findById(15L)).thenAnswer(invocacion -> Optional.ofNullable(guardada.get()));
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
    void elAltaYLaCopiaUsanElDiaDeLimaYLaValidezEsQuinceDiasDespues() {
        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC));
        servicio.crear(pedido());
        assertEquals(LocalDate.of(2026, 11, 15), guardada.get().getFechaCotizacion());
        assertEquals(LocalDate.of(2026, 11, 30), guardada.get().getFechaValidez());

        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T05:00:00Z"), ZoneOffset.UTC));
        servicio.crear(pedido());
        assertEquals(LocalDate.of(2026, 11, 16), guardada.get().getFechaCotizacion());
        assertEquals(LocalDate.of(2026, 12, 1), guardada.get().getFechaValidez());

        when(cotizaciones.findById(20L)).thenReturn(Optional.of(original()));

        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC));
        servicio.duplicar(20L);
        assertEquals(LocalDate.of(2026, 11, 15), guardada.get().getFechaCotizacion());
        assertEquals(LocalDate.of(2026, 11, 30), guardada.get().getFechaValidez());

        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T05:00:00Z"), ZoneOffset.UTC));
        servicio.duplicar(20L);
        assertEquals(LocalDate.of(2026, 11, 16), guardada.get().getFechaCotizacion());
        assertEquals(LocalDate.of(2026, 12, 1), guardada.get().getFechaValidez());

        ArgumentCaptor<LocalDate> dias = ArgumentCaptor.forClass(LocalDate.class);
        verify(tasaIgv, times(4)).porcentajeVigente(dias.capture());
        assertEquals(List.of(
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 16)), dias.getAllValues());
    }

    @Test
    void altaConEurNoGuardaNiUsaElVigente() {
        Moneda euro = new Moneda();
        euro.setIdMoneda(3);
        euro.setCodigoMoneda("EUR");
        when(monedas.findById(3)).thenReturn(Optional.of(euro));
        when(monedas.findByCodigoMoneda("EUR")).thenReturn(Optional.of(euro));

        for (String alias : List.of("EUR", "eur", " EUR ")) {
            CotizacionRequest porAlias = pedido();
            porAlias.setIdMoneda(null);
            porAlias.setMoneda(alias);
            BusinessException error = assertThrows(BusinessException.class, () -> servicio.crear(porAlias));
            assertEquals("La cotización solo admite soles o dólares.", error.getMessage());
        }

        CotizacionRequest porId = pedido();
        porId.setIdMoneda(3);
        porId.setMoneda(null);
        BusinessException porIdError = assertThrows(BusinessException.class, () -> servicio.crear(porId));
        assertEquals("La cotización solo admite soles o dólares.", porIdError.getMessage());

        verify(cotizaciones, never()).save(any());
        verify(detalles, never()).save(any());
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void duplicarEurNoGuarda() {
        Cotizacion original = original();
        Moneda euro = new Moneda();
        euro.setCodigoMoneda("EUR");
        original.setMoneda(euro);
        when(cotizaciones.findById(20L)).thenReturn(Optional.of(original));

        BusinessException error = assertThrows(BusinessException.class, () -> servicio.duplicar(20L));

        assertEquals("La cotización solo admite soles o dólares.", error.getMessage());
        verify(cotizaciones, never()).save(any());
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

    @Test
    void altaUsdConCambioYFechaValidosGuardaEseValor() {
        dolar();
        LocalDate fecha = LocalDate.of(2026, 11, 12);
        when(tiposCambio.findByMoneda_CodigoMonedaAndFecha("USD", fecha))
                .thenReturn(Optional.of(fila("3.7100")));
        CotizacionRequest pedido = pedido();
        pedido.setIdMoneda(2);
        pedido.setTipoCambio(new BigDecimal("3.7100"));
        pedido.setFechaTipoCambio(fecha);

        servicio.crear(pedido);

        assertEquals(0, new BigDecimal("3.7100").compareTo(guardada.get().getTipoCambioUsado()));
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void altaUsdConValorDistintoSeRechazaYNoGuarda() {
        dolar();
        LocalDate fecha = LocalDate.of(2026, 11, 12);
        when(tiposCambio.findByMoneda_CodigoMonedaAndFecha("USD", fecha))
                .thenReturn(Optional.of(fila("3.7100")));
        CotizacionRequest pedido = pedidoUsd(fecha, new BigDecimal("3.8000"));

        BusinessException error = assertThrows(BusinessException.class, () -> servicio.crear(pedido));

        assertEquals(
                "El tipo de cambio cambió. Recalcula los importes y revísalos antes de solicitar aprobación.",
                error.getMessage());
        verify(cotizaciones, never()).save(any());
        verify(detalles, never()).save(any());
    }

    @Test
    void altaUsdConFechaInexistenteSeRechaza() {
        dolar();
        LocalDate fecha = LocalDate.of(2026, 1, 1);
        when(tiposCambio.findByMoneda_CodigoMonedaAndFecha("USD", fecha)).thenReturn(Optional.empty());
        CotizacionRequest pedido = pedidoUsd(fecha, new BigDecimal("3.7100"));

        BusinessException error = assertThrows(BusinessException.class, () -> servicio.crear(pedido));

        assertEquals(
                "El tipo de cambio cambió. Recalcula los importes y revísalos antes de solicitar aprobación.",
                error.getMessage());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void altaUsdConSoloUnoDeLosDosCamposSeRechaza() {
        dolar();
        CotizacionRequest soloValor = pedido();
        soloValor.setIdMoneda(2);
        soloValor.setTipoCambio(new BigDecimal("3.7100"));
        CotizacionRequest soloFecha = pedido();
        soloFecha.setIdMoneda(2);
        soloFecha.setFechaTipoCambio(LocalDate.of(2026, 11, 12));

        BusinessException sinFecha = assertThrows(BusinessException.class, () -> servicio.crear(soloValor));
        BusinessException sinValor = assertThrows(BusinessException.class, () -> servicio.crear(soloFecha));

        assertEquals("Indica el tipo de cambio y su fecha", sinFecha.getMessage());
        assertEquals("Indica el tipo de cambio y su fecha", sinValor.getMessage());
        verify(cotizaciones, never()).save(any());
    }

    @Test
    void altaUsdSinLosCamposUsaElVigente() {
        dolar();
        CotizacionRequest pedido = pedido();
        pedido.setIdMoneda(2);

        servicio.crear(pedido);

        assertEquals(0, VIGENTE.compareTo(guardada.get().getTipoCambioUsado()));
        verify(tipoCambioCotizacion).vigente();
    }

    @Test
    void altaPenIgnoraLosCamposPropuestos() {
        CotizacionRequest pedido = pedido();
        pedido.setTipoCambio(new BigDecimal("9.9999"));
        pedido.setFechaTipoCambio(LocalDate.of(2026, 11, 12));

        servicio.crear(pedido);

        assertEquals(0, VIGENTE.compareTo(guardada.get().getTipoCambioUsado()));
        verify(tiposCambio, never()).findByMoneda_CodigoMonedaAndFecha(any(), any());
    }

    @Test
    void importesInvalidosSeRechazan() {
        CotizacionRequest cantidadCero = pedido();
        cantidadCero.getLineas().get(0).setCantidad(BigDecimal.ZERO);
        CotizacionRequest precioNegativo = pedido();
        precioNegativo.getLineas().get(0).setPrecioUnitario(new BigDecimal("-1"));
        CotizacionRequest descuentoMayor = pedido();
        descuentoMayor.getLineas().get(0).setDescuentoUnitario(new BigDecimal("11"));

        assertEquals("La cantidad debe ser mayor a 0",
                assertThrows(BusinessException.class, () -> servicio.crear(cantidadCero)).getMessage());
        assertEquals("El precio no puede ser negativo",
                assertThrows(BusinessException.class, () -> servicio.crear(precioNegativo)).getMessage());
        assertEquals("El descuento no puede ser mayor que el precio",
                assertThrows(BusinessException.class, () -> servicio.crear(descuentoMayor)).getMessage());
        verify(cotizaciones, never()).save(any());
        verify(detalles, never()).save(any());
    }

    @Test
    void enviarConservaElCambioAunqueElVigenteSeaOtro() {
        Cotizacion cotizacion = aprobada(new BigDecimal("3.7500"));
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(tipoCambioCotizacion.vigente()).thenReturn(new BigDecimal("3.9000"));
        estadoEnviada();

        servicio.marcarComoEnviadaSiBorrador(15L, usuario(), "cliente@mina.pe");

        assertEquals("ENVIADA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertEquals(0, new BigDecimal("3.7500").compareTo(cotizacion.getTipoCambioUsado()));
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void enviarSinCambioGuardadoLoDejaEnNull() {
        Cotizacion cotizacion = aprobada(null);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(tipoCambioCotizacion.vigente()).thenReturn(new BigDecimal("3.9000"));
        estadoEnviada();

        servicio.marcarComoEnviadaSiBorrador(15L, usuario(), "cliente@mina.pe");

        assertEquals("ENVIADA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertEquals(null, cotizacion.getTipoCambioUsado());
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void siBrevoFallaSigueAprobadaInternaYElCambioNoCambia() {
        BigDecimal guardado = new BigDecimal("3.7100");
        Cotizacion cotizacion = aprobada(guardado);
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(tipoCambioCotizacion.vigente()).thenReturn(new BigDecimal("3.9000"));
        when(pdf.generarPdfCotizacion(any())).thenReturn(new byte[] {1});
        when(transacciones.execute(any())).thenAnswer(invocacion -> {
            TransactionCallback<?> callback = invocacion.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(mail.enviarCorreo(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);

        EnviarCotizacionRequest envio = new EnviarCotizacionRequest();
        envio.setCorreoDestinatario("cliente@mina.pe");
        envio.setAsunto("Cotización");
        servicio.enviar(15L, envio);

        assertEquals("APROBADA_INTERNA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertEquals(0, guardado.compareTo(cotizacion.getTipoCambioUsado()));
        verify(tipoCambioCotizacion, never()).vigente();
    }

    @Test
    void descargarPdfNoConsultaElTipoDeCambioVigente() {
        Cotizacion cotizacion = aprobada(new BigDecimal("3.7100"));
        cotizacion.getEstadoCotizacion().setCodigoEstado("BORRADOR");
        when(cotizaciones.findById(15L)).thenReturn(Optional.of(cotizacion));
        when(pdf.generarPdfCotizacion(any())).thenReturn(new byte[] {1});

        servicio.descargarPdf(15L);

        verify(tipoCambioCotizacion, never()).vigente();
    }

    private void dolar() {
        Moneda dolar = new Moneda();
        dolar.setIdMoneda(2);
        dolar.setCodigoMoneda("USD");
        when(monedas.findById(2)).thenReturn(Optional.of(dolar));
    }

    private static TipoCambio fila(String valor) {
        TipoCambio fila = new TipoCambio();
        fila.setValor(new BigDecimal(valor));
        return fila;
    }

    private static CotizacionRequest pedidoUsd(LocalDate fecha, BigDecimal valor) {
        CotizacionRequest pedido = pedido();
        pedido.setIdMoneda(2);
        pedido.setTipoCambio(valor);
        pedido.setFechaTipoCambio(fecha);
        return pedido;
    }

    private void estadoEnviada() {
        EstadoCotizacion enviada = new EstadoCotizacion();
        enviada.setIdEstadoCotizacion(5);
        enviada.setCodigoEstado("ENVIADA");
        enviada.setNombreEstado("Enviada");
        when(estados.findByCodigoEstado("ENVIADA")).thenReturn(Optional.of(enviada));
        when(seguimientos.findByCotizacion(15L)).thenReturn(List.of());
    }

    private static Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("ana");
        return usuario;
    }

    private static Cotizacion aprobada(BigDecimal tipoCambio) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setIdEstadoCotizacion(4);
        estado.setCodigoEstado("APROBADA_INTERNA");
        estado.setNombreEstado("Aprobada interna");
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Minera Sur");
        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        Moneda dolar = new Moneda();
        dolar.setCodigoMoneda("USD");
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(15L);
        cotizacion.setCodigoCotizacion("COT-15");
        cotizacion.setCliente(cliente);
        cotizacion.setMoneda(dolar);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setFechaCotizacion(LocalDate.of(2026, 11, 12));
        cotizacion.setTipoCambioUsado(tipoCambio);
        return cotizacion;
    }

    private static Cotizacion original() {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Minera Sur");
        cliente.setTercero(tercero);
        Moneda sol = new Moneda();
        sol.setCodigoMoneda("PEN");
        Cotizacion original = new Cotizacion();
        original.setIdCotizacion(20L);
        original.setCodigoCotizacion("COT-20");
        original.setCliente(cliente);
        original.setMoneda(sol);
        original.setFechaCotizacion(LocalDate.of(2026, 1, 1));
        original.setFechaValidez(LocalDate.of(2026, 1, 16));
        return original;
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
