package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class DespachoServiceTest {

    @Mock private DespachoRepository despachos;
    @Mock private OrdenCompraRepository ordenes;
    @Mock private CotizacionDetalleRepository detalles;
    @Mock private UsuarioRepository usuarios;
    @Mock private FeriadoService feriados;

    private OrdenCompra orden;
    private final LocalDateTime inicio = LocalDateTime.of(2026, 3, 13, 10, 0);

    @BeforeEach
    void setUp() {
        Usuario ana = new Usuario();
        ana.setIdUsuario(4L);
        ana.setNombreUsuario("ana");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "n/a", List.of()));
        orden = orden("APROBADA");
        org.mockito.Mockito.lenient().when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(ana));
        org.mockito.Mockito.lenient().when(ordenes.findById(8L)).thenReturn(Optional.of(orden));
        org.mockito.Mockito.lenient().when(despachos.save(any())).thenAnswer(inv -> {
            com.genlogs.app.model.Despacho guardado = inv.getArgument(0);
            guardado.setIdDespacho(15L);
            return guardado;
        });
        org.mockito.Mockito.lenient().when(feriados.esFeriado(any())).thenReturn(false);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nacionalDuraUnDiaYDosDias() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea("NACIONAL"), linea("NACIONAL")));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.NACIONAL, respuesta.getOrigen());
        assertEquals(inicio.plusHours(24), respuesta.getPlazoDesde());
        assertEquals(inicio.plusHours(48), respuesta.getPlazoHasta());
        assertEquals(false, respuesta.isOrigenIncompleto());
    }

    @Test
    void unImportadoLlevaElPlazoLargoYLoMixtoTambien() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea("NACIONAL"), linea("IMPORTADO")));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.IMPORTADO, respuesta.getOrigen());
        assertEquals(LocalDateTime.of(2026, 3, 20, 0, 0), respuesta.getPlazoDesde());
        assertEquals(LocalDateTime.of(2026, 3, 24, 0, 0), respuesta.getPlazoHasta());
    }

    @Test
    void origenNuloCuentaComoNacionalYMarcaIncompleto() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(null), linea("NACIONAL")));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.NACIONAL, respuesta.getOrigen());
        assertTrue(respuesta.isOrigenIncompleto());
        assertEquals(inicio.plusHours(24), respuesta.getPlazoDesde());
    }

    @Test
    void rechazaOcInexistenteInactivaCotizacionNoAprobadaYDespachoActivo() {
        when(ordenes.findById(9L)).thenReturn(Optional.empty());
        assertTrue(assertThrows(BusinessException.class, () -> servicio().crear(9L, inicio))
                .getMessage().contains("No existe"));

        orden.setStatus("I");
        assertTrue(assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio))
                .getMessage().contains("no está activa"));

        orden.setStatus("A");
        orden.getCotizacion().getEstadoCotizacion().setCodigoEstado("ENVIADA");
        assertTrue(assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio))
                .getMessage().contains("no está aprobada"));

        orden.getCotizacion().getEstadoCotizacion().setCodigoEstado("APROBADA");
        when(despachos.existsByOrdenCompra_IdOrdenCompraAndStatus(8L, "A")).thenReturn(true);
        assertTrue(assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio))
                .getMessage().contains("despacho activo"));
    }

    @Test
    void aduanaSoloEnImportadoYNoSeSaltaEtapa() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea("NACIONAL")));
        servicio().crear(8L, inicio);
        Despacho guardado = capturar();
        when(despachos.findById(15L)).thenReturn(Optional.of(guardado));

        BusinessException aduana = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_ADUANA));
        assertTrue(aduana.getMessage().contains("no pasa por aduana"));

        servicio().avanzar(15L, EtapaDespacho.EN_PREPARACION);
        assertEquals(EtapaDespacho.EN_PREPARACION, guardado.getEtapa());
        assertThrows(BusinessException.class, () -> servicio().avanzar(15L, EtapaDespacho.ENTREGADO));
    }

    @Test
    void importadoSiEntraAAduana() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea("IMPORTADO")));
        servicio().crear(8L, inicio);
        Despacho guardado = capturar();
        when(despachos.findById(15L)).thenReturn(Optional.of(guardado));

        servicio().avanzar(15L, EtapaDespacho.EN_PREPARACION);
        var respuesta = servicio().avanzar(15L, EtapaDespacho.EN_ADUANA);

        assertEquals(EtapaDespacho.EN_ADUANA, respuesta.getEtapa());
        assertEquals("En aduana", respuesta.getEtapaNombre());
    }

    private Despacho capturar() {
        ArgumentCaptor<Despacho> captor = ArgumentCaptor.forClass(Despacho.class);
        verify(despachos).save(captor.capture());
        return captor.getValue();
    }

    private DespachoService servicio() {
        return new DespachoService(despachos, ordenes, detalles, usuarios, new PlazoDespacho(feriados));
    }

    private OrdenCompra orden(String estadoCodigo) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(estadoCodigo);
        Cliente cliente = new Cliente();
        cliente.setIdCliente(2L);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(3L);
        cotizacion.setCliente(cliente);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setStatus("A");
        OrdenCompra ordenCompra = new OrdenCompra();
        ordenCompra.setIdOrdenCompra(8L);
        ordenCompra.setNumeroOrdenCompra("0008-10");
        ordenCompra.setCotizacion(cotizacion);
        ordenCompra.setStatus("A");
        return ordenCompra;
    }

    private static CotizacionDetalle linea(String origen) {
        CotizacionDetalle detalle = new CotizacionDetalle();
        detalle.setOrigen(origen);
        detalle.setCantidad(BigDecimal.ONE);
        return detalle;
    }
}
