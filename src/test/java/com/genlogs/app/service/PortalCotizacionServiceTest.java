package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
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
        cotizacion.setFechaValidez(LocalDate.now().minusDays(1));
        when(cotizacionRepository.findById(7L)).thenReturn(Optional.of(cotizacion));

        BusinessException vencida = assertThrows(BusinessException.class, () -> servicio.aprobar(7L, null));
        assertTrue(vencida.getMessage().contains("vencida"));

        cotizacion.setFechaValidez(LocalDate.now().plusDays(2));
        when(estadoCotizacionRepository.findByCodigoEstado("APROBADA")).thenReturn(Optional.of(estado("APROBADA")));
        when(cotizacionRepository.findTotalesByCotizacion(7L)).thenReturn(Optional.empty());
        when(cotizacionDetalleRepository.findByCotizacion(7L)).thenReturn(List.of());

        servicio.aprobar(7L, null);
        assertEquals("APROBADA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertThrows(BusinessException.class, () -> servicio.aprobar(7L, null));
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
        cotizacion.setFechaValidez(LocalDate.now().plusDays(5));
        cotizacion.setStatus("A");
        return cotizacion;
    }

    private static EstadoCotizacion estado(String codigo) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(codigo);
        estado.setNombreEstado("Enviada");
        if ("APROBADA".equals(codigo)) {
            estado.setNombreEstado("Aprobada");
        }
        return estado;
    }
}
