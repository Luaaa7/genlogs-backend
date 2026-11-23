package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.dto.PortalCotizacionResponse;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PortalCotizacionServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CotizacionRepository cotizacionRepository;
    @Mock private CotizacionDetalleRepository cotizacionDetalleRepository;
    @Mock private EstadoCotizacionRepository estadoCotizacionRepository;
    @Mock private SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    @Mock private OrdenCompraRepository ordenCompraRepository;

    private PortalCotizacionService servicio;
    private Cliente mia;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        servicio = new PortalCotizacionService(usuarioRepository, cotizacionRepository, cotizacionDetalleRepository,
                estadoCotizacionRepository, seguimientoCotizacionRepository, ordenCompraRepository);
        mia = new Cliente();
        mia.setIdCliente(5L);
        usuario = new Usuario();
        usuario.setNombreUsuario("mina");
        usuario.setCliente(mia);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mina", "n/a", List.of()));
        lenient().when(usuarioRepository.findWithClienteByNombreUsuario("mina")).thenReturn(Optional.of(usuario));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noListaBorradorNiPendienteNiAprobacionInterna() {
        when(cotizacionRepository.findByCliente(5L)).thenReturn(List.of(
                cotizacion(1L, mia, "BORRADOR"),
                cotizacion(2L, mia, "PENDIENTE_APROBACION"),
                cotizacion(3L, mia, "APROBADA_INTERNA"),
                cotizacion(4L, mia, "ENVIADA")));
        when(cotizacionRepository.findTotalesByCotizacion(4L)).thenReturn(Optional.empty());

        List<PortalCotizacionResponse> lista = servicio.listar();

        assertEquals(1, lista.size());
        assertEquals(4L, lista.get(0).getId());
        assertEquals("Enviada", lista.get(0).getEstado());
    }

    @Test
    void otraEmpresaYBorradorPropioRespondenIgual() {
        when(cotizacionRepository.findById(8L)).thenReturn(Optional.of(cotizacion(8L, cliente(9L), "ENVIADA")));
        when(cotizacionRepository.findById(3L)).thenReturn(Optional.of(cotizacion(3L, mia, "BORRADOR")));

        assertThrows(ResourceNotFoundException.class, () -> servicio.detalle(8L));
        assertThrows(ResourceNotFoundException.class, () -> servicio.detalle(3L));
    }

    @Test
    void vencidaNoSeApruebaYLaSegundaTampoco() {
        Cotizacion cotizacion = cotizacion(7L, mia, "ENVIADA");
        cotizacion.setFechaValidez(LocalDate.now(PlazoDespacho.LIMA).minusDays(1));
        when(cotizacionRepository.findById(7L)).thenReturn(Optional.of(cotizacion));

        BusinessException vencida = assertThrows(BusinessException.class, () -> servicio.aprobar(7L, null));
        assertEquals("La cotización está vencida", vencida.getMessage());

        cotizacion.setFechaValidez(LocalDate.now(PlazoDespacho.LIMA).plusDays(2));
        when(estadoCotizacionRepository.findByCodigoEstado("ACEPTADA_CLIENTE")).thenReturn(Optional.of(estado("ACEPTADA_CLIENTE")));
        when(cotizacionRepository.findTotalesByCotizacion(7L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(7L)).thenReturn(List.of());

        servicio.aprobar(7L, null);
        assertEquals("ACEPTADA_CLIENTE", cotizacion.getEstadoCotizacion().getCodigoEstado());
        BusinessException resuelta = assertThrows(BusinessException.class, () -> servicio.aprobar(7L, null));
        assertEquals("Esta cotización ya no se puede aprobar ni rechazar", resuelta.getMessage());
    }

    @Test
    void aLas2359DeLimaSeApruebaYAmedianocheLanzaVencida() {
        Cotizacion cotizacion = cotizacion(12L, mia, "ENVIADA");
        cotizacion.setFechaValidez(LocalDate.of(2026, 11, 15));
        when(cotizacionRepository.findById(12L)).thenReturn(Optional.of(cotizacion));
        when(estadoCotizacionRepository.findByCodigoEstado("ACEPTADA_CLIENTE")).thenReturn(Optional.of(estado("ACEPTADA_CLIENTE")));
        when(cotizacionRepository.findTotalesByCotizacion(12L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(12L)).thenReturn(List.of());

        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T04:59:00Z"), ZoneOffset.UTC));
        servicio.aprobar(12L, null);
        assertEquals("ACEPTADA_CLIENTE", cotizacion.getEstadoCotizacion().getCodigoEstado());

        cotizacion.setEstadoCotizacion(estado("ENVIADA"));
        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T05:00:00Z"), ZoneOffset.UTC));
        BusinessException vencida = assertThrows(BusinessException.class, () -> servicio.rechazar(12L, null));
        assertEquals("La cotización está vencida", vencida.getMessage());
        assertEquals("ENVIADA", cotizacion.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void unaYaResueltaLanzaYaResueltaAunqueLaFechaDeLimaSirva() {
        Cotizacion cotizacion = cotizacion(13L, mia, "RECHAZADA");
        cotizacion.setFechaValidez(LocalDate.of(2026, 11, 15));
        when(cotizacionRepository.findById(13L)).thenReturn(Optional.of(cotizacion));
        servicio.setClock(Clock.fixed(Instant.parse("2026-11-16T04:59:00Z"), ZoneOffset.UTC));

        BusinessException resuelta = assertThrows(BusinessException.class, () -> servicio.aprobar(13L, null));

        assertEquals("Esta cotización ya no se puede aprobar ni rechazar", resuelta.getMessage());
        assertEquals("RECHAZADA", cotizacion.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void unaVencidaNoSeApruebaNiSeRechaza() {
        Cotizacion cotizacion = cotizacion(11L, mia, "VENCIDA");
        when(cotizacionRepository.findById(11L)).thenReturn(Optional.of(cotizacion));

        BusinessException alAprobar = assertThrows(BusinessException.class, () -> servicio.aprobar(11L, null));
        BusinessException alRechazar = assertThrows(BusinessException.class, () -> servicio.rechazar(11L, null));
        assertEquals("Esta cotización ya no se puede aprobar ni rechazar", alAprobar.getMessage());
        assertEquals("Esta cotización ya no se puede aprobar ni rechazar", alRechazar.getMessage());
        assertEquals("VENCIDA", cotizacion.getEstadoCotizacion().getCodigoEstado());
    }

    @Test
    void elJsonNoTraeMargenNiDatosInternos() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                PortalCotizacionResponse.builder().id(1L).estado("Enviada").total(new BigDecimal("100")).build());
        assertFalse(json.contains("margen"));
        assertFalse(json.contains("precioProveedor"));
        assertFalse(json.contains("tipoCambio"));
        assertFalse(json.contains("aviso"));
    }

    @Test
    void elPortalSoloMuestraElNumeroDeLaOcDeLaMinera() throws Exception {
        Cotizacion cotizacion = cotizacion(4L, mia, "ENVIADA");
        cotizacion.setFechaCotizacion(null);
        cotizacion.setFechaValidez(null);
        when(cotizacionRepository.findById(4L)).thenReturn(Optional.of(cotizacion));
        when(cotizacionRepository.findTotalesByCotizacion(4L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(4L)).thenReturn(List.of());
        when(ordenCompraRepository.numerosOcCliente(4L)).thenReturn(List.of("4500012345"));

        String json = new ObjectMapper().writeValueAsString(servicio.detalle(4L));

        assertTrue(json.contains("4500012345"));
        assertFalse(json.contains("montoOc"));
        assertFalse(json.contains("monedaLeida"));
        assertFalse(json.contains("avisoMonto"));
        assertFalse(json.contains("ocLeida"));
        assertFalse(json.contains("ocConfirmada"));
        assertFalse(json.contains("urlArchivo"));
        assertFalse(json.contains("margen"));
        assertFalse(json.contains("costo"));
    }

    @Test
    void elJsonDelPortalNoTraeElOrigenDeLaLinea() throws Exception {
        Cotizacion cotizacion = cotizacion(4L, mia, "ENVIADA");
        cotizacion.setFechaCotizacion(null);
        cotizacion.setFechaValidez(null);
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setOrigen(OrigenRepuesto.IMPORTADO);
        linea.setDescripcionPersonalizada("rodamiento");
        linea.setCantidad(BigDecimal.ONE);
        when(cotizacionRepository.findById(4L)).thenReturn(Optional.of(cotizacion));
        when(cotizacionRepository.findTotalesByCotizacion(4L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(4L)).thenReturn(List.of(linea));
        when(ordenCompraRepository.numerosOcCliente(4L)).thenReturn(List.of());

        String json = new ObjectMapper().writeValueAsString(servicio.detalle(4L));

        assertTrue(json.contains("rodamiento"));
        assertFalse(json.contains("origen"), json);
        assertFalse(json.contains("IMPORTADO"), json);
    }

    @Test
    void unItemSinDescripcionPersonalizadaMuestraElNombreDelProducto() {
        Producto producto = new Producto();
        producto.setNombreProducto("Rodamiento 6205");
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setProducto(producto);
        linea.setDescripcionPersonalizada(" ");

        assertEquals("Rodamiento 6205", lineaDelPortal(linea));
    }

    @Test
    void unItemConDescripcionPersonalizadaLaConserva() {
        Producto producto = new Producto();
        producto.setNombreProducto("Rodamiento 6205");
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setProducto(producto);
        linea.setDescripcionPersonalizada("Rodamiento para faja");

        assertEquals("Rodamiento para faja", lineaDelPortal(linea));
    }

    private String lineaDelPortal(CotizacionDetalle linea) {
        Cotizacion cotizacion = cotizacion(4L, mia, "ENVIADA");
        cotizacion.setFechaCotizacion(null);
        cotizacion.setFechaValidez(null);
        when(cotizacionRepository.findById(4L)).thenReturn(Optional.of(cotizacion));
        when(cotizacionRepository.findTotalesByCotizacion(4L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(4L)).thenReturn(List.of(linea));
        when(ordenCompraRepository.numerosOcCliente(4L)).thenReturn(List.of());
        return servicio.detalle(4L).getLineas().get(0).getDescripcion();
    }

    @Test
    void elDetalleTraeElMotivoSoloSiEstaAnulada() throws Exception {
        Cotizacion anulada = cotizacion(9L, mia, "ANULADA");
        anulada.getEstadoCotizacion().setNombreEstado("Anulada");
        anulada.setFechaCotizacion(null);
        anulada.setFechaValidez(null);
        when(cotizacionRepository.findById(9L)).thenReturn(Optional.of(anulada));
        when(cotizacionRepository.findTotalesByCotizacion(9L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(9L)).thenReturn(List.of());
        when(ordenCompraRepository.numerosOcCliente(9L)).thenReturn(List.of());
        SeguimientoCotizacion seguimiento = new SeguimientoCotizacion();
        seguimiento.setEstadoCotizacion(estado("ANULADA"));
        seguimiento.setComentario("El cliente pidió anular por teléfono");
        when(seguimientoCotizacionRepository.findByCotizacion(9L)).thenReturn(List.of(seguimiento));

        String json = new ObjectMapper().writeValueAsString(servicio.detalle(9L));

        assertTrue(json.contains("El cliente pidió anular por teléfono"));
        assertTrue(json.contains("motivoAnulacion"));
        assertFalse(json.contains("margen"));
        assertFalse(json.contains("costo"));
        assertFalse(json.contains("precioProveedor"));

        Cotizacion enviada = cotizacion(4L, mia, "ENVIADA");
        enviada.setFechaCotizacion(null);
        enviada.setFechaValidez(null);
        when(cotizacionRepository.findById(4L)).thenReturn(Optional.of(enviada));
        when(cotizacionRepository.findTotalesByCotizacion(4L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(4L)).thenReturn(List.of());
        when(ordenCompraRepository.numerosOcCliente(4L)).thenReturn(List.of());

        String abierta = new ObjectMapper().writeValueAsString(servicio.detalle(4L));

        assertFalse(abierta.contains("motivoAnulacion"));
    }

    @Test
    void subtotalMasIgvEsElTotal() {
        Cotizacion cotizacion = cotizacion(4L, mia, "ENVIADA");
        when(cotizacionRepository.findById(4L)).thenReturn(Optional.of(cotizacion));
        when(cotizacionRepository.findByCliente(5L)).thenReturn(List.of(cotizacion));
        when(cotizacionRepository.findTotalesByCotizacion(4L)).thenReturn(Optional.of(totales("100.00", "18.00", "118.00")));
        when(cotizacionDetalleRepository.findByCotizacion(4L)).thenReturn(List.of());
        when(ordenCompraRepository.numerosOcCliente(4L)).thenReturn(List.of());

        PortalCotizacionResponse detalle = servicio.detalle(4L);
        PortalCotizacionResponse listada = servicio.listar().get(0);

        assertEquals(0, detalle.getSubtotal().add(detalle.getIgv()).compareTo(detalle.getTotal()));
        assertEquals(new BigDecimal("100.00"), detalle.getSubtotal());
        assertEquals(new BigDecimal("18.00"), detalle.getIgv());
        assertEquals(new BigDecimal("118.00"), detalle.getTotal());
        assertEquals(0, listada.getSubtotal().add(listada.getIgv()).compareTo(listada.getTotal()));
    }

    private static Cliente cliente(Long id) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(id);
        return cliente;
    }

    private static Cotizacion cotizacion(Long id, Cliente cliente, String codigo) {
        EstadoCotizacion estado = estado(codigo);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(id);
        cotizacion.setCodigoCotizacion("COT-" + id);
        cotizacion.setCliente(cliente);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setFechaValidez(LocalDate.now(PlazoDespacho.LIMA).plusDays(5));
        cotizacion.setStatus("A");
        return cotizacion;
    }

    private static CotizacionRepository.TotalesCotizacionProjection totales(String subtotal, String igv, String total) {
        return new CotizacionRepository.TotalesCotizacionProjection() {
            @Override
            public BigDecimal getSubtotal() {
                return new BigDecimal(subtotal);
            }

            @Override
            public BigDecimal getIgv() {
                return new BigDecimal(igv);
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    private static EstadoCotizacion estado(String codigo) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(codigo);
        estado.setNombreEstado("Enviada");
        if ("ACEPTADA_CLIENTE".equals(codigo)) {
            estado.setNombreEstado("Aceptada por el cliente");
        }
        return estado;
    }
}
