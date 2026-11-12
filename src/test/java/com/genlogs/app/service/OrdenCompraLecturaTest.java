package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.ConfirmarOcRequest;
import com.genlogs.app.dto.OrdenCompraResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoOrdenCompra;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository.TotalesCotizacionProjection;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class OrdenCompraLecturaTest {

    @org.mockito.Mock private OrdenCompraRepository ordenes;
    @org.mockito.Mock private CotizacionRepository cotizaciones;
    @org.mockito.Mock private UsuarioRepository usuarios;
    @org.mockito.Mock private CloudinaryService cloudinary;
    @org.mockito.Mock private LectorOcPdf lector;

    private OrdenCompra orden;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        ana = new Usuario();
        ana.setIdUsuario(9L);
        ana.setNombreUsuario("ana");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "n/a", java.util.List.of()));
        orden = orden();
        org.mockito.Mockito.lenient().when(ordenes.findById(1L)).thenReturn(Optional.of(orden));
        org.mockito.Mockito.lenient().when(ordenes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(ana));
        org.mockito.Mockito.lenient().when(cloudinary.subirDocumento(any(), any())).thenReturn("https://cdn/oc.pdf");
        TotalesCotizacionProjection totales = org.mockito.Mockito.mock(TotalesCotizacionProjection.class);
        org.mockito.Mockito.lenient().when(totales.getTotal()).thenReturn(new BigDecimal("100.00"));
        org.mockito.Mockito.lenient().when(cotizaciones.findTotalesByCotizacion(3L)).thenReturn(Optional.of(totales));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unPdfLegibleGuardaNumeroMontoYMarcaLeida() throws Exception {
        OrdenCompraService servicio = servicio(new LectorOcPdf());
        byte[] pdf = LectorOcPdfTest.pdf("Orden de Compra 4500012345", "Fecha 15/03/2026", "Total PEN 250.00");

        OrdenCompraResponse respuesta = servicio.agregarArchivo(1L, archivo(pdf));

        assertEquals("4500012345", orden.getNumeroOcCliente());
        assertEquals(new BigDecimal("250.00"), orden.getMontoOcCliente());
        assertTrue(orden.isOcLeidaAuto());
        assertEquals(java.time.LocalDate.of(2026, 3, 15), orden.getFechaEmisionCliente());
        assertEquals("https://cdn/oc.pdf", orden.getUrlArchivo());
        assertNull(orden.getOcConfirmadaPor());
        assertTrue(respuesta.getAvisoMonto().contains("250.00"));
        assertTrue(respuesta.getAvisoMonto().contains("100.00"));
        assertEquals("PEN", respuesta.getMonedaLeida());
        assertEquals("PEN", orden.getMonedaOcCliente());

        OrdenCompraResponse recarga = servicio.buscarPorId(1L);
        assertEquals(respuesta.getAvisoMonto(), recarga.getAvisoMonto());
        assertEquals("PEN", recarga.getMonedaLeida());
    }

    @Test
    void unArchivoQueNoEsPdfSeGuardaSinDatos() {
        OrdenCompraService servicio = servicio(lector);
        orden.setNumeroOcCliente("999");
        orden.setOcConfirmadaPor(ana);
        orden.setOcLeidaAuto(true);

        OrdenCompraResponse respuesta = servicio.agregarArchivo(1L,
                archivo("hola".getBytes(StandardCharsets.UTF_8)));

        verify(lector, never()).leer(any(byte[].class));
        assertEquals("https://cdn/oc.pdf", orden.getUrlArchivo());
        assertNull(orden.getNumeroOcCliente());
        assertNull(orden.getMontoOcCliente());
        assertFalse(orden.isOcLeidaAuto());
        assertNull(orden.getOcConfirmadaPor());
        assertNull(respuesta.getAvisoMonto());
    }

    @Test
    void bytesBasuraConCabeceraPdfNoDanError() {
        OrdenCompraService servicio = servicio(new LectorOcPdf());
        byte[] basura = new byte[] {'%', 'P', 'D', 'F', '-', 1, 2, 3};

        OrdenCompraResponse respuesta = servicio.agregarArchivo(1L, archivo(basura));

        assertEquals("https://cdn/oc.pdf", respuesta.getUrlArchivo());
        assertNull(orden.getNumeroOcCliente());
        assertFalse(orden.isOcLeidaAuto());
    }

    @Test
    void montoIgualNoAvisaYMonedaDistintaNoAfirmaCoincidencia() throws Exception {
        TotalesCotizacionProjection iguales = org.mockito.Mockito.mock(TotalesCotizacionProjection.class);
        when(iguales.getTotal()).thenReturn(new BigDecimal("250.00"));
        when(cotizaciones.findTotalesByCotizacion(3L)).thenReturn(Optional.of(iguales));
        OrdenCompraService servicio = servicio(new LectorOcPdf());

        OrdenCompraResponse igual = servicio.agregarArchivo(1L,
                archivo(LectorOcPdfTest.pdf("Total PEN 250.00")));
        assertNull(igual.getAvisoMonto());

        orden.getCotizacion().getMoneda().setCodigoMoneda("USD");
        OrdenCompraResponse otraMoneda = servicio.agregarArchivo(1L,
                archivo(LectorOcPdfTest.pdf("Total PEN 250.00")));
        assertEquals("La OC está en PEN y la cotización en USD; no se compara el monto.",
                otraMoneda.getAvisoMonto());
        assertEquals(otraMoneda.getAvisoMonto(), servicio.buscarPorId(1L).getAvisoMonto());
    }

    @Test
    void resubirCambiaElAvisoYSinMonedaUsaElMensajeNuevo() throws Exception {
        OrdenCompraService servicio = servicio(new LectorOcPdf());
        OrdenCompraResponse distinto = servicio.agregarArchivo(1L,
                archivo(LectorOcPdfTest.pdf("Total PEN 250.00")));
        assertTrue(distinto.getAvisoMonto().contains("250.00"));

        OrdenCompraResponse sinMoneda = servicio.agregarArchivo(1L,
                archivo(LectorOcPdfTest.pdf("Total 100.00")));
        assertNull(orden.getMonedaOcCliente());
        assertEquals("No se pudo determinar la moneda del PDF; verifica que coincida con la de la cotización.",
                sinMoneda.getAvisoMonto());
        assertEquals(sinMoneda.getAvisoMonto(), servicio.buscarPorId(1L).getAvisoMonto());
    }

    @Test
    void unCodigoQueNoSonTresLetrasSeGuardaNuloYElListadoAvisaEnUnaConsulta() {
        when(lector.leer(any(byte[].class)))
                .thenReturn(new LecturaOc(null, null, new BigDecimal("80.00"), "euro"));
        OrdenCompraService servicio = servicio(lector);
        OrdenCompraResponse leida = servicio.agregarArchivo(1L, archivo(new byte[] {'%', 'P', 'D', 'F', '-', 'x'}));
        assertNull(orden.getMonedaOcCliente());
        assertTrue(leida.getAvisoMonto().startsWith("No se pudo determinar la moneda"));

        org.mockito.Mockito.clearInvocations(cotizaciones);
        orden.setMontoOcCliente(new BigDecimal("250.00"));
        orden.setMonedaOcCliente("pen ");
        org.mockito.Mockito.when(ordenes.buscar(any(), org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(orden)));
        CotizacionRepository.TotalesCotizacionPorIdProjection fila =
                org.mockito.Mockito.mock(CotizacionRepository.TotalesCotizacionPorIdProjection.class);
        when(fila.getIdCotizacion()).thenReturn(3L);
        when(fila.getTotal()).thenReturn(new BigDecimal("100.00"));
        when(cotizaciones.findTotalesPorCotizaciones(any())).thenReturn(java.util.List.of(fila));

        OrdenCompraResponse enLista = servicio.listar(null, null, 0, 10).getContent().get(0);
        assertEquals("PEN", enLista.getMonedaLeida());
        assertTrue(enLista.getAvisoMonto().contains("250.00"));
        verify(cotizaciones, never()).findTotalesByCotizacion(any());
        verify(cotizaciones).findTotalesPorCotizaciones(any());
    }

    @Test
    void confirmarGuardaAlUsuarioYUnaCerradaNoSeCorrige() {
        OrdenCompraService servicio = servicio(lector);
        ConfirmarOcRequest request = new ConfirmarOcRequest();
        request.setNumeroOc("4500012345");
        request.setMonto(new BigDecimal("100.00"));
        request.setMoneda("PEN");

        OrdenCompraResponse respuesta = servicio.confirmar(1L, request);

        assertEquals("4500012345", orden.getNumeroOcCliente());
        assertEquals(9L, orden.getOcConfirmadaPor().getIdUsuario());
        assertEquals("PEN", orden.getMonedaOcCliente());
        assertTrue(respuesta.getOcConfirmada());
        assertNull(respuesta.getAvisoMonto());

        ConfirmarOcRequest sinMoneda = new ConfirmarOcRequest();
        sinMoneda.setNumeroOc("4500012345");
        sinMoneda.setMonto(new BigDecimal("100.00"));
        servicio.confirmar(1L, sinMoneda);
        assertEquals("PEN", orden.getMonedaOcCliente());

        request.setMoneda("usd");
        OrdenCompraResponse otra = servicio.confirmar(1L, request);
        assertEquals("USD", orden.getMonedaOcCliente());
        assertEquals("La OC está en USD y la cotización en PEN; no se compara el monto.", otra.getAvisoMonto());

        orden.getEstadoOrdenCompra().setEsFinal(true);
        orden.getEstadoOrdenCompra().setNombreEstado("Atendida");
        assertThrows(BusinessException.class, () -> servicio.confirmar(1L, request));
    }

    @Test
    void siFallaElGuardadoElArchivoYaSubidoNoSeBorra() {
        when(ordenes.save(any())).thenThrow(new RuntimeException("la base no respondió"));
        OrdenCompraService servicio = servicio(lector);

        assertThrows(RuntimeException.class,
                () -> servicio.agregarArchivo(1L, archivo("nota.txt".getBytes(StandardCharsets.UTF_8))));

        verify(cloudinary).subirDocumento(any(), any());
        verify(cloudinary, never()).eliminarPorUrl(any(), any());
    }

    private OrdenCompraService servicio(LectorOcPdf lectorOc) {
        return new OrdenCompraService(ordenes, cotizaciones, null, null, usuarios, cloudinary, null, lectorOc);
    }

    private MockMultipartFile archivo(byte[] bytes) {
        return new MockMultipartFile("archivo", "oc.pdf", "application/pdf", bytes);
    }

    private static OrdenCompra orden() {
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Mina Sur");
        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        Moneda moneda = new Moneda();
        moneda.setCodigoMoneda("PEN");
        moneda.setSimbolo("S/");
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(3L);
        cotizacion.setCodigoCotizacion("COT-3");
        cotizacion.setCliente(cliente);
        cotizacion.setMoneda(moneda);
        EstadoOrdenCompra estado = new EstadoOrdenCompra();
        estado.setCodigoEstado("RECIBIDA");
        estado.setNombreEstado("Recibida");
        estado.setEsFinal(false);
        OrdenCompra orden = new OrdenCompra();
        orden.setIdOrdenCompra(1L);
        orden.setCotizacion(cotizacion);
        orden.setEstadoOrdenCompra(estado);
        orden.setNumeroOrdenCompra("0001-10");
        orden.setUrlArchivo("https://cdn/anterior.pdf");
        return orden;
    }
}
