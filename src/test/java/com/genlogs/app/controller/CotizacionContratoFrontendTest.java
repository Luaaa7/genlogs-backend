package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.genlogs.app.exception.GlobalExceptionHandler;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
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
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.CloudinaryService;
import com.genlogs.app.service.CotizacionService;
import com.genlogs.app.service.MailService;
import com.genlogs.app.service.pdf.PdfGenerationService;

/**
 * Fija el contrato entre el backend y el frontend de cotizaciones.
 *
 * El frontend (NuevaCotizacionPage / CotizacionDetallePage) manda y espera
 * nombres distintos a los canÃ³nicos: "clienteId", "moneda", "condicionPago",
 * "detalles" y "estadoNuevo"; y espera "estadoCodigo", "monedaCodigo",
 * "clienteNombre" en la respuesta. Antes esos nombres se ignoraban y el
 * backend respondÃ­a 400 o devolvÃ­a la respuesta incompleta.
 */
class CotizacionContratoFrontendTest {

    private static final String USUARIO = "vendedor";

    private CotizacionRepository cotizacionRepository;
    private CotizacionDetalleRepository cotizacionDetalleRepository;
    private EstadoCotizacionRepository estadoCotizacionRepository;
    private SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private AdjuntoCotizacionRepository adjuntoCotizacionRepository;
    private ClienteRepository clienteRepository;
    private MonedaRepository monedaRepository;
    private CondicionPagoRepository condicionPagoRepository;
    private UsuarioRepository usuarioRepository;
    private UnidadMedidaRepository unidadMedidaRepository;
    private EntityManager entityManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cotizacionRepository = mock(CotizacionRepository.class);
        cotizacionDetalleRepository = mock(CotizacionDetalleRepository.class);
        estadoCotizacionRepository = mock(EstadoCotizacionRepository.class);
        seguimientoCotizacionRepository = mock(SeguimientoCotizacionRepository.class);
        adjuntoCotizacionRepository = mock(AdjuntoCotizacionRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        condicionPagoRepository = mock(CondicionPagoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        unidadMedidaRepository = mock(UnidadMedidaRepository.class);

        // Mock EntityManager for native queries (auditoria BD)
        entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(null);

        CotizacionService service = crearServicio();

        mockMvc = MockMvcBuilders.standaloneSetup(new CotizacionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USUARIO, null, List.of()));
    }

        private CotizacionService crearServicio() {
                try {
                        java.lang.reflect.Constructor<?> constructor = CotizacionService.class
                                        .getDeclaredConstructors()[0];
                        Object[] argumentos = new Object[constructor.getParameterCount()];

                        for (int i = 0; i < argumentos.length; i++) {
                                Class<?> tipo = constructor.getParameterTypes()[i];
                                if (tipo == CotizacionRepository.class) {
                                        argumentos[i] = cotizacionRepository;
                                } else if (tipo == CotizacionDetalleRepository.class) {
                                        argumentos[i] = cotizacionDetalleRepository;
                                } else if (tipo == EstadoCotizacionRepository.class) {
                                        argumentos[i] = estadoCotizacionRepository;
                                } else if (tipo == SeguimientoCotizacionRepository.class) {
                                        argumentos[i] = seguimientoCotizacionRepository;
                                } else if (tipo == AdjuntoCotizacionRepository.class) {
                                        argumentos[i] = adjuntoCotizacionRepository;
                                } else if (tipo == ClienteRepository.class) {
                                        argumentos[i] = clienteRepository;
                                } else if (tipo == MonedaRepository.class) {
                                        argumentos[i] = monedaRepository;
                                } else if (tipo == CondicionPagoRepository.class) {
                                        argumentos[i] = condicionPagoRepository;
                                } else if (tipo == UsuarioRepository.class) {
                                        argumentos[i] = usuarioRepository;
                                } else if (tipo == UnidadMedidaRepository.class) {
                                        argumentos[i] = unidadMedidaRepository;
                                } else if (tipo == EntityManager.class) {
                                        argumentos[i] = entityManager;
                                } else if (tipo == org.springframework.transaction.support.TransactionTemplate.class) {
                                        argumentos[i] = new org.springframework.transaction.support.TransactionTemplate(
                                                        mock(org.springframework.transaction.PlatformTransactionManager.class));
                                } else {
                                        argumentos[i] = mock(tipo);
                                }
                        }

                        constructor.setAccessible(true);
                        return (CotizacionService) constructor.newInstance(argumentos);
                } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("No se pudo crear CotizacionService", e);
                }
        }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crear_conNombresDelFrontend_aceptaClienteMonedaCondicionPagoYDetalles() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setIdTercero(10L);
        tercero.setRazonSocial("ACME S.A.C.");
        cliente.setTercero(tercero);
        SectorEconomico sector = new SectorEconomico();
        sector.setIdSectorEconomico(1);
        sector.setNombreSector("MinerÃ­a");
        cliente.setSectorEconomico(sector);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        Moneda moneda = new Moneda();
        moneda.setIdMoneda(2);
        moneda.setCodigoMoneda("PEN");
        moneda.setNombreMoneda("Sol peruano");
        moneda.setSimbolo("S/");
        when(monedaRepository.findByCodigoMoneda("PEN")).thenReturn(Optional.of(moneda));
        when(monedaRepository.findById(2)).thenReturn(Optional.of(moneda));

        CondicionPago condicion = new CondicionPago();
        condicion.setIdCondicionPago(3);
        condicion.setNombreCondicion("CONTADO");
        when(condicionPagoRepository.findByNombreCondicion("CONTADO")).thenReturn(Optional.of(condicion));
        when(condicionPagoRepository.findById(3)).thenReturn(Optional.of(condicion));

        EstadoCotizacion borrador = new EstadoCotizacion();
        borrador.setIdEstadoCotizacion(1);
        borrador.setCodigoEstado("BORRADOR");
        borrador.setNombreEstado("Borrador");
        when(estadoCotizacionRepository.findByCodigoEstado("BORRADOR")).thenReturn(Optional.of(borrador));

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(7L);
        usuario.setNombreUsuario(USUARIO);
        when(usuarioRepository.findByNombreUsuario(USUARIO)).thenReturn(Optional.of(usuario));

        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        unidad.setCodigoUnidad("UND");
        unidad.setNombreUnidad("Unidad");
        when(unidadMedidaRepository.findFirstByStatusOrderByIdUnidadMedidaAsc("A"))
                .thenReturn(Optional.of(unidad));

        when(cotizacionRepository.save(any(Cotizacion.class))).thenAnswer(inv -> {
            Cotizacion guardada = inv.getArgument(0);
            guardada.setIdCotizacion(99L);
            return guardada;
        });
        when(cotizacionRepository.findCodigoCotizacionGenerado(99L)).thenReturn(Optional.of("COT-0001"));
        when(cotizacionDetalleRepository.findByCotizacion(anyLong())).thenReturn(List.of());
        when(seguimientoCotizacionRepository.findByCotizacion(anyLong())).thenReturn(List.of());
        when(adjuntoCotizacionRepository.findByCotizacion(anyLong())).thenReturn(List.of());

        String payload = """
                {
                  "clienteId": 1,
                  "moneda": "PEN",
                  "condicionPago": "CONTADO",
                  "observaciones": "Desde el frontend",
                  "detalles": [
                    { "producto": "Placa de desgaste", "cantidad": 2, "precioUnitario": 100.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/cotizaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoCotizacion").value("COT-0001"))
                .andExpect(jsonPath("$.monedaCodigo").value("PEN"))
                .andExpect(jsonPath("$.estadoCodigo").value("BORRADOR"))
                .andExpect(jsonPath("$.clienteNombre").value("ACME S.A.C."));

        // La línea descriptiva (sin idProducto/idServicio) se guarda con la
        // descripción del frontend y con la unidad de medida por defecto.
        ArgumentCaptor<CotizacionDetalle> lineaCaptor = ArgumentCaptor.forClass(CotizacionDetalle.class);
        verify(cotizacionDetalleRepository).save(lineaCaptor.capture());
        CotizacionDetalle linea = lineaCaptor.getValue();
        assertEquals("Placa de desgaste", linea.getDescripcionPersonalizada());
        assertNotNull(linea.getUnidadMedida());
        assertEquals("Unidad", linea.getUnidadMedida().getNombreUnidad());
    }

    @Test
    void cambiarEstado_conEstadoNuevo_aceptaElAliasDelFrontend() throws Exception {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(5L);
        cotizacion.setCodigoCotizacion("COT-0005");
        EstadoCotizacion actual = new EstadoCotizacion();
        actual.setIdEstadoCotizacion(1);
        actual.setCodigoEstado("BORRADOR");
        actual.setNombreEstado("Borrador");
        actual.setEsFinal(false);
        cotizacion.setEstadoCotizacion(actual);
        when(cotizacionRepository.findById(5L)).thenReturn(Optional.of(cotizacion));

        EstadoCotizacion enviada = new EstadoCotizacion();
        enviada.setIdEstadoCotizacion(2);
        enviada.setCodigoEstado("ENVIADA");
        enviada.setNombreEstado("Enviada");
        when(estadoCotizacionRepository.findByCodigoEstado("ENVIADA")).thenReturn(Optional.of(enviada));

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(7L);
        usuario.setNombreUsuario(USUARIO);
        when(usuarioRepository.findByNombreUsuario(USUARIO)).thenReturn(Optional.of(usuario));

        when(cotizacionRepository.save(any(Cotizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cotizacionDetalleRepository.findByCotizacion(anyLong())).thenReturn(List.of());
        when(seguimientoCotizacionRepository.findByCotizacion(anyLong())).thenReturn(List.of());
        when(adjuntoCotizacionRepository.findByCotizacion(anyLong())).thenReturn(List.of());

        mockMvc.perform(patch("/api/cotizaciones/5/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoNuevo\":\"ENVIADA\",\"observaciones\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCodigo").value("ENVIADA"));
    }

    @Test
    void listar_devuelveLosAliasQueUsaElFrontend() throws Exception {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(5L);
        cotizacion.setCodigoCotizacion("COT-0005");
        cotizacion.setFechaCotizacion(LocalDate.now());
        cotizacion.setFechaValidez(LocalDate.now().plusDays(15));
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setIdEstadoCotizacion(4);
        estado.setCodigoEstado("APROBADA");
        estado.setNombreEstado("Aprobada");
        cotizacion.setEstadoCotizacion(estado);
        Moneda moneda = new Moneda();
        moneda.setIdMoneda(2);
        moneda.setCodigoMoneda("PEN");
        moneda.setSimbolo("S/");
        cotizacion.setMoneda(moneda);
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        Tercero tercero = new Tercero();
        tercero.setIdTercero(10L);
        tercero.setRazonSocial("ACME S.A.C.");
        cliente.setTercero(tercero);
        cotizacion.setCliente(cliente);

        when(cotizacionRepository.buscarPaginado(any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(cotizacion)));

        mockMvc.perform(get("/api/cotizaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estadoCodigo").value("APROBADA"))
                .andExpect(jsonPath("$.content[0].estadoCotizacion").value("APROBADA"))
                .andExpect(jsonPath("$.content[0].monedaCodigo").value("PEN"))
                .andExpect(jsonPath("$.content[0].clienteNombre").value("ACME S.A.C."))
                .andExpect(jsonPath("$.content[0].id").value(5))
                // "moneda" debe ser el CÓDIGO: el frontend lo pasa a
                // Intl.NumberFormat({currency}) y con un símbolo revienta.
                .andExpect(jsonPath("$.content[0].moneda").value("PEN"))
                .andExpect(jsonPath("$.content[0].monedaSimbolo").value("S/"))
                // Alias de paginación que declara el frontend.
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(1));
    }

    /**
     * El detalle es lo que renderiza la pantalla CotizacionDetallePage:
     * - "tamanio" del adjunto SIEMPRE numérico (el frontend lo formatea sin
     *   validar y con undefined revienta la pantalla);
     * - "estadoAnterior" de cada seguimiento (para pintar "Borrador → Aprobada");
     * - "clienteEmail" para prellenar el correo de envío.
     */
    @Test
    void detalle_devuelveTamanioEstadoAnteriorYCorreoDelCliente() throws Exception {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(5L);
        cotizacion.setCodigoCotizacion("COT-0005");
        cotizacion.setFechaCotizacion(LocalDate.now());
        cotizacion.setFechaValidez(LocalDate.now().plusDays(15));

        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setIdEstadoCotizacion(4);
        estado.setCodigoEstado("APROBADA");
        estado.setNombreEstado("Aprobada");
        cotizacion.setEstadoCotizacion(estado);

        Moneda moneda = new Moneda();
        moneda.setIdMoneda(2);
        moneda.setCodigoMoneda("PEN");
        moneda.setSimbolo("S/");
        cotizacion.setMoneda(moneda);

        Tercero tercero = new Tercero();
        tercero.setIdTercero(10L);
        tercero.setRazonSocial("ACME S.A.C.");
        tercero.setCorreo("compras@acme.pe");
        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        cliente.setTercero(tercero);
        cotizacion.setCliente(cliente);

        when(cotizacionRepository.findById(5L)).thenReturn(Optional.of(cotizacion));
        when(cotizacionDetalleRepository.findByCotizacion(5L)).thenReturn(List.of());

        AdjuntoCotizacion adjunto = new AdjuntoCotizacion();
        adjunto.setIdAdjunto(1L);
        adjunto.setNombreArchivo("ficha.pdf");
        adjunto.setUrlArchivo("https://res.cloudinary.com/demo/raw/upload/ficha.pdf");
        adjunto.setDateCreate(LocalDateTime.now());
        adjunto.setUserCreate(USUARIO);
        when(adjuntoCotizacionRepository.findByCotizacion(5L)).thenReturn(List.of(adjunto));

        // La consulta viene en orden descendente (más reciente primero).
        when(seguimientoCotizacionRepository.findByCotizacion(5L)).thenReturn(List.of(
                seguimiento(2L, "APROBADA", "Aprobada"),
                seguimiento(1L, "BORRADOR", "Borrador")));

        mockMvc.perform(get("/api/cotizaciones/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteEmail").value("compras@acme.pe"))
                .andExpect(jsonPath("$.moneda").value("PEN"))
                .andExpect(jsonPath("$.monedaSimbolo").value("S/"))
                .andExpect(jsonPath("$.adjuntos[0].tamanio").value(0))
                .andExpect(jsonPath("$.adjuntos[0].tipoArchivo").value("pdf"))
                .andExpect(jsonPath("$.adjuntos[0].urlCloudinary").value("https://res.cloudinary.com/demo/raw/upload/ficha.pdf"))
                .andExpect(jsonPath("$.seguimientos[0].estadoNuevo").value("APROBADA"))
                .andExpect(jsonPath("$.seguimientos[0].estadoAnterior").value("BORRADOR"))
                .andExpect(jsonPath("$.seguimientos[1].estadoNuevo").value("BORRADOR"));
    }

    private SeguimientoCotizacion seguimiento(Long id, String codigo, String nombre) {
        EstadoCotizacion e = new EstadoCotizacion();
        e.setIdEstadoCotizacion(id.intValue());
        e.setCodigoEstado(codigo);
        e.setNombreEstado(nombre);
        SeguimientoCotizacion s = new SeguimientoCotizacion();
        s.setIdSeguimiento(id);
        s.setEstadoCotizacion(e);
        s.setFechaEvento(LocalDateTime.now());
        s.setComentario("evento " + codigo);
        return s;
    }

    @Test
    void listar_consultaLosTotalesDeTodaLaPaginaEnUnaSolaQuery() throws Exception {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(5L);
        when(cotizacionRepository.buscarPaginado(any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(cotizacion)));

        mockMvc.perform(get("/api/cotizaciones"))
                .andExpect(status().isOk());

        // Una sola llamada a la vista para toda la pÃ¡gina (no una por fila).
        verify(cotizacionRepository).findTotalesPorCotizaciones(any());
        verify(cotizacionRepository, org.mockito.Mockito.never())
                .findTotalesByCotizacion(anyLong());
    }
}
