package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.ConfirmarSstRequest;
import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.enums.TipoSstDespacho;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.DespachoSst;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.DespachoSstRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class DespachoServiceTest {

    @Mock private DespachoRepository despachos;
    @Mock private OrdenCompraRepository ordenes;
    @Mock private CotizacionDetalleRepository detalles;
    @Mock private ProductoRepository productos;
    @Mock private DespachoSstRepository validaciones;
    @Mock private EntityManager entityManager;
    @Mock private UsuarioRepository usuarios;
    @Mock private FeriadoService feriados;

    private final java.util.ArrayList<DespachoSst> filasSst = new java.util.ArrayList<>();
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
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.NACIONAL), linea(OrigenRepuesto.NACIONAL)));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.NACIONAL, respuesta.getOrigen());
        assertEquals(inicio.plusHours(24), respuesta.getPlazoDesde());
        assertEquals(inicio.plusHours(48), respuesta.getPlazoHasta());
        assertEquals(false, respuesta.isOrigenIncompleto());
    }

    @Test
    void unaLineaImportadaSaleImportadaConPlazoLargo() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.IMPORTADO)));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.IMPORTADO, respuesta.getOrigen());
        assertEquals(false, respuesta.isOrigenIncompleto());
        assertEquals(LocalDateTime.of(2026, 3, 20, 0, 0), respuesta.getPlazoDesde());
        assertEquals(LocalDateTime.of(2026, 3, 24, 0, 0), respuesta.getPlazoHasta());
    }

    @Test
    void unImportadoLlevaElPlazoLargoYLoMixtoTambien() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(
                linea(OrigenRepuesto.NACIONAL), linea(OrigenRepuesto.IMPORTADO)));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.IMPORTADO, respuesta.getOrigen());
        assertEquals(LocalDateTime.of(2026, 3, 20, 0, 0), respuesta.getPlazoDesde());
        assertEquals(LocalDateTime.of(2026, 3, 24, 0, 0), respuesta.getPlazoHasta());
    }

    @Test
    void sinOrigenNiProductoQuedaNacionalEIncompleto() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(null)));
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.NACIONAL, respuesta.getOrigen());
        assertTrue(respuesta.isOrigenIncompleto());
        assertEquals(inicio.plusHours(24), respuesta.getPlazoDesde());
    }

    @Test
    void productoImportadoCompletaElOrigenQueElDetalleNoTrae() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(lineaConProducto(null, 7L)));
        when(productos.findOrigenById(7L)).thenReturn(List.of("IMPORTADO"));

        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.IMPORTADO, respuesta.getOrigen());
        assertEquals(false, respuesta.isOrigenIncompleto());
        assertEquals(LocalDateTime.of(2026, 3, 20, 0, 0), respuesta.getPlazoDesde());
        assertEquals(LocalDateTime.of(2026, 3, 24, 0, 0), respuesta.getPlazoHasta());
    }

    @Test
    void elDetalleImportadoMandaSobreElProductoNacional() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(lineaConProducto(OrigenRepuesto.IMPORTADO, 7L)));

        var respuesta = servicio().crear(8L, inicio);

        assertEquals(OrigenRepuesto.IMPORTADO, respuesta.getOrigen());
        assertEquals(false, respuesta.isOrigenIncompleto());
        org.mockito.Mockito.verify(productos, org.mockito.Mockito.never()).findOrigenById(any());
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
        orden.setOcConfirmadaPor(null);
        BusinessException sinConfirmar = assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio));
        assertEquals(DespachoService.OC_SIN_CONFIRMAR, sinConfirmar.getMessage());
        verify(despachos, never()).existsByOrdenCompra_IdOrdenCompraAndStatus(8L, "A");

        orden.setOcConfirmadaPor(confirmador());
        when(despachos.existsByOrdenCompra_IdOrdenCompraAndStatus(8L, "A")).thenReturn(true);
        assertTrue(assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio))
                .getMessage().contains("despacho activo"));
        verify(despachos, never()).save(any());
    }

    @Test
    void sinConfirmarNoGuardaElDespacho() {
        orden.setOcConfirmadaPor(null);

        BusinessException error = assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio));

        assertEquals(DespachoService.OC_SIN_CONFIRMAR, error.getMessage());
        verify(despachos, never()).save(any());
    }

    @Test
    void aceptadaPorElClienteCreaElDespachoYOtroEstadoSigueRechazado() {
        orden.getCotizacion().getEstadoCotizacion().setCodigoEstado("ACEPTADA_CLIENTE");
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.NACIONAL)));

        var respuesta = servicio().crear(8L, inicio);

        assertEquals(EtapaDespacho.REGISTRADO, respuesta.getEtapa());
        verify(despachos).save(any());

        orden.getCotizacion().getEstadoCotizacion().setCodigoEstado("ENVIADA");
        assertTrue(assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio))
                .getMessage().contains("no está aprobada"));
    }

    @Test
    void ocConfirmadaCreaElDespachoRegistradoConOrigenYPlazo() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.NACIONAL)));

        var respuesta = servicio().crear(8L, inicio);

        assertEquals(EtapaDespacho.REGISTRADO, respuesta.getEtapa());
        assertEquals(OrigenRepuesto.NACIONAL, respuesta.getOrigen());
        assertEquals(inicio.plusHours(24), respuesta.getPlazoDesde());
        assertEquals(inicio.plusHours(48), respuesta.getPlazoHasta());
        verify(despachos).save(any());
    }

    @Test
    void elPrimerIntentoSinConfirmarSeRechazaYElSegundoCreaElDespacho() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.NACIONAL)));
        orden.setOcConfirmadaPor(null);

        BusinessException error = assertThrows(BusinessException.class, () -> servicio().crear(8L, inicio));
        assertEquals(DespachoService.OC_SIN_CONFIRMAR, error.getMessage());
        verify(despachos, never()).save(any());

        orden.setOcConfirmadaPor(confirmador());
        var respuesta = servicio().crear(8L, inicio);

        assertEquals(EtapaDespacho.REGISTRADO, respuesta.getEtapa());
        assertEquals(OrigenRepuesto.NACIONAL, respuesta.getOrigen());
        verify(despachos).save(any());
    }

    @Test
    void sinDocumentosNoPasaATransitoYTrasRegularizarSi() {
        Despacho guardado = listoParaSalir();
        guardarDocumentosSst();

        BusinessException bloqueo = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO));

        assertTrue(bloqueo.getMessage().startsWith(DespachoBloqueoSst.PREFIJO));
        assertEquals(EtapaDespacho.EN_PREPARACION, guardado.getEtapa());

        LocalDate vence = LocalDate.now(PlazoDespacho.LIMA).plusDays(10);
        servicioSst().confirmar(15L, pedidoSst(TipoSstDespacho.PASE, vence, "P-1"));
        servicioSst().confirmar(15L, pedidoSst(TipoSstDespacho.SCTR, vence, "S-1"));

        var respuesta = servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO);

        assertEquals(EtapaDespacho.EN_TRANSITO, respuesta.getEtapa());
        assertEquals(EtapaDespacho.EN_TRANSITO, guardado.getEtapa());
    }

    @Test
    void sctrVencidoNoEntregaYTrasRegularizarSi() {
        Despacho guardado = listoParaSalir();
        guardado.setEtapa(EtapaDespacho.EN_TRANSITO);
        guardarDocumentosSst();
        LocalDate ayer = LocalDate.now(PlazoDespacho.LIMA).minusDays(1);
        filasSst.add(sst(TipoSstDespacho.PASE, LocalDate.now(PlazoDespacho.LIMA).plusDays(10), "P-1"));
        filasSst.add(sst(TipoSstDespacho.SCTR, ayer, "S-viejo"));

        BusinessException bloqueo = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.ENTREGADO));

        assertTrue(bloqueo.getMessage().contains("SCTR vencido"));
        assertEquals(EtapaDespacho.EN_TRANSITO, guardado.getEtapa());

        LocalDate vence = LocalDate.now(PlazoDespacho.LIMA).plusDays(10);
        servicioSst().confirmar(15L, pedidoSst(TipoSstDespacho.SCTR, vence, "S-nuevo"));

        var respuesta = servicio().avanzar(15L, EtapaDespacho.ENTREGADO);

        assertEquals(EtapaDespacho.ENTREGADO, respuesta.getEtapa());
        assertEquals(EtapaDespacho.ENTREGADO, guardado.getEtapa());
    }

    @Test
    void aduanaSoloEnImportadoYNoSeSaltaEtapa() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.NACIONAL)));
        servicio().crear(8L, inicio);
        Despacho guardado = capturar();
        when(despachos.findById(15L)).thenReturn(Optional.of(guardado));

        BusinessException aduana = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_ADUANA));
        assertTrue(aduana.getMessage().contains("no pasa por aduana"));

        servicio().avanzar(15L, EtapaDespacho.EN_PREPARACION);
        assertEquals(EtapaDespacho.EN_PREPARACION, guardado.getEtapa());
        assertThrows(BusinessException.class, () -> servicio().avanzar(15L, EtapaDespacho.REGISTRADO));
        assertThrows(BusinessException.class, () -> servicio().avanzar(15L, EtapaDespacho.ENTREGADO));
    }

    @Test
    void importadoSiEntraAAduana() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.IMPORTADO)));
        servicio().crear(8L, inicio);
        Despacho guardado = capturar();
        when(despachos.findById(15L)).thenReturn(Optional.of(guardado));

        servicio().avanzar(15L, EtapaDespacho.EN_PREPARACION);
        var respuesta = servicio().avanzar(15L, EtapaDespacho.EN_ADUANA);

        assertEquals(EtapaDespacho.EN_ADUANA, respuesta.getEtapa());
        assertEquals("En aduana", respuesta.getEtapaNombre());
    }

    @Test
    void preparacionYAduanaNoExigenSst() {
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea(OrigenRepuesto.IMPORTADO)));
        servicio().crear(8L, inicio);
        Despacho guardado = capturar();
        when(despachos.findById(15L)).thenReturn(Optional.of(guardado));

        servicio().avanzar(15L, EtapaDespacho.EN_PREPARACION);
        var respuesta = servicio().avanzar(15L, EtapaDespacho.EN_ADUANA);

        assertEquals(EtapaDespacho.EN_ADUANA, respuesta.getEtapa());
    }

    @Test
    void sinSctrSinPaseYSinNingunoBloqueanLaSalida() {
        Despacho guardado = listoParaSalir();
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A"))
                .thenReturn(List.of(sst(TipoSstDespacho.PASE, LocalDate.now(PlazoDespacho.LIMA), null)));
        BusinessException sinSctr = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO));
        assertEquals("DESPACHO_BLOQUEADO_SST: falta SCTR vigente", sinSctr.getMessage());

        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A"))
                .thenReturn(List.of(sst(TipoSstDespacho.SCTR, LocalDate.now(PlazoDespacho.LIMA), null)));
        BusinessException sinPase = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO));
        assertEquals("DESPACHO_BLOQUEADO_SST: falta pase", sinPase.getMessage());

        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A")).thenReturn(List.of());
        BusinessException ninguno = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO));
        assertTrue(ninguno.getMessage().startsWith(DespachoBloqueoSst.PREFIJO));
        assertEquals("DESPACHO_BLOQUEADO_SST: falta pase y SCTR vigente", ninguno.getMessage());
        assertEquals(EtapaDespacho.EN_PREPARACION, guardado.getEtapa());
    }

    @Test
    void ambosVigentesDejanSalirYElMismoDiaTodaviaVale() {
        Despacho guardado = listoParaSalir();
        LocalDate hoy = LocalDate.now(PlazoDespacho.LIMA);
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A")).thenReturn(List.of(
                sst(TipoSstDespacho.PASE, hoy, "P-1"),
                sst(TipoSstDespacho.SCTR, hoy, "S-1")));

        var respuesta = servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO);

        assertEquals(EtapaDespacho.EN_TRANSITO, respuesta.getEtapa());
        assertEquals(false, respuesta.getSst().isBloqueaAvance());
        assertNull(respuesta.getSst().getMensaje());
        assertEquals(EtapaDespacho.EN_TRANSITO, guardado.getEtapa());
    }

    @Test
    void sctrVencidoBloqueaYElDiaSiguienteTambien() {
        listoParaSalir();
        LocalDate ayer = LocalDate.now(PlazoDespacho.LIMA).minusDays(1);
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A")).thenReturn(List.of(
                sst(TipoSstDespacho.PASE, LocalDate.now(PlazoDespacho.LIMA), null),
                sst(TipoSstDespacho.SCTR, ayer, null)));

        BusinessException vencido = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO));

        assertTrue(vencido.getMessage().startsWith(DespachoBloqueoSst.PREFIJO));
        assertEquals("DESPACHO_BLOQUEADO_SST: SCTR vencido el "
                + ayer.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), vencido.getMessage());
    }

    @Test
    void faltaPaseYSctrVencidoVanEnUnSoloMensaje() {
        listoParaSalir();
        LocalDate ayer = LocalDate.now(PlazoDespacho.LIMA).minusDays(1);
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A"))
                .thenReturn(List.of(sst(TipoSstDespacho.SCTR, ayer, "POL-9")));

        BusinessException combinado = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.EN_TRANSITO));

        assertEquals("DESPACHO_BLOQUEADO_SST: falta pase y SCTR vencido el "
                + ayer.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), combinado.getMessage());
    }

    @Test
    void elGetInternoTraeElBloqueoYLaReferencia() {
        listoParaSalir();
        LocalDate hoy = LocalDate.now(PlazoDespacho.LIMA);
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A"))
                .thenReturn(List.of(sst(TipoSstDespacho.SCTR, hoy, "POL-9")));

        var sst = servicio().buscar(15L).getSst();

        assertEquals(true, sst.isBloqueaAvance());
        assertEquals("DESPACHO_BLOQUEADO_SST: falta pase", sst.getMensaje());
        assertNull(sst.getPase());
        assertEquals("POL-9", sst.getSctr().getReferencia());
        assertEquals(hoy, sst.getSctr().getVencimiento());
    }

    @Test
    void laEntregaTambienQuedaBloqueadaSiFaltaElDocumento() {
        Despacho guardado = listoParaSalir();
        guardado.setEtapa(EtapaDespacho.EN_TRANSITO);
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A")).thenReturn(List.of());

        BusinessException bloqueo = assertThrows(BusinessException.class,
                () -> servicio().avanzar(15L, EtapaDespacho.ENTREGADO));

        assertTrue(bloqueo.getMessage().startsWith(DespachoBloqueoSst.PREFIJO));
        assertEquals(EtapaDespacho.EN_TRANSITO, guardado.getEtapa());
    }

    private Despacho listoParaSalir() {
        Despacho guardado = new Despacho();
        guardado.setIdDespacho(15L);
        guardado.setOrdenCompra(orden);
        guardado.setCotizacion(orden.getCotizacion());
        guardado.setOrigen(OrigenRepuesto.NACIONAL);
        guardado.setEtapa(EtapaDespacho.EN_PREPARACION);
        guardado.setStatus("A");
        Usuario ana = new Usuario();
        ana.setIdUsuario(4L);
        guardado.setUsuario(ana);
        when(despachos.findById(15L)).thenReturn(Optional.of(guardado));
        return guardado;
    }

    private static DespachoSst sst(TipoSstDespacho tipo, LocalDate vencimiento, String referencia) {
        DespachoSst fila = new DespachoSst();
        fila.setTipo(tipo);
        fila.setFechaVencimiento(vencimiento);
        fila.setReferencia(referencia);
        fila.setStatus("A");
        return fila;
    }

    private Despacho capturar() {
        ArgumentCaptor<Despacho> captor = ArgumentCaptor.forClass(Despacho.class);
        verify(despachos).save(captor.capture());
        return captor.getValue();
    }

    private DespachoService servicio() {
        return new DespachoService(despachos, ordenes, detalles, productos, usuarios, new PlazoDespacho(feriados),
                new DespachoSstService(validaciones, despachos, usuarios, entityManager));
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
        ordenCompra.setOcConfirmadaPor(confirmador());
        return ordenCompra;
    }

    private static Usuario confirmador() {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(4L);
        usuario.setNombreUsuario("ana");
        return usuario;
    }

    private void guardarDocumentosSst() {
        org.mockito.Mockito.lenient()
                .when(validaciones.findByDespacho_IdDespachoAndTipoAndStatus(eq(15L), any(), eq("A")))
                .thenAnswer(inv -> {
                    TipoSstDespacho tipo = inv.getArgument(1);
                    return filasSst.stream()
                            .filter(f -> f.getTipo() == tipo && "A".equals(f.getStatus()))
                            .findFirst();
                });
        org.mockito.Mockito.lenient()
                .when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A"))
                .thenAnswer(inv -> filasSst.stream().filter(f -> "A".equals(f.getStatus())).toList());
        org.mockito.Mockito.lenient().when(validaciones.save(any())).thenAnswer(inv -> {
            DespachoSst fila = inv.getArgument(0);
            if (!filasSst.contains(fila)) {
                filasSst.add(fila);
            }
            return fila;
        });
    }

    private DespachoSstService servicioSst() {
        return new DespachoSstService(validaciones, despachos, usuarios, entityManager);
    }

    private static ConfirmarSstRequest pedidoSst(TipoSstDespacho tipo, LocalDate vencimiento, String referencia) {
        ConfirmarSstRequest request = new ConfirmarSstRequest();
        request.setTipo(tipo);
        request.setFechaVencimiento(vencimiento);
        request.setReferencia(referencia);
        return request;
    }

    private static CotizacionDetalle linea(OrigenRepuesto origen) {
        CotizacionDetalle detalle = new CotizacionDetalle();
        detalle.setOrigen(origen);
        detalle.setCantidad(BigDecimal.ONE);
        return detalle;
    }

    private static CotizacionDetalle lineaConProducto(OrigenRepuesto origen, long idProducto) {
        Producto producto = new Producto();
        producto.setIdProducto(idProducto);
        CotizacionDetalle detalle = linea(origen);
        detalle.setProducto(producto);
        return detalle;
    }
}
