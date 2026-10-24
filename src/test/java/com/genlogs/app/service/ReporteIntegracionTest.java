package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ReportePreviewResponse;
import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.EstadoFacturacion;
import com.genlogs.app.model.EstadoOrdenCompra;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.Marca;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoComprobante;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.CategoriaServicioRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.EstadoFacturacionRepository;
import com.genlogs.app.repository.EstadoOrdenCompraRepository;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.MarcaRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoComprobanteRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Integración real del módulo de Reportes contra H2: la vista previa
 * (POST /api/reportes/preview) y la descarga (POST /api/reportes/generar)
 * deben devolver exactamente los mismos datos, con el mismo periodo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReporteIntegracionTest {

    /** Periodo del caso reportado: 23/08/2026 a 02/10/2026. */
    private static final LocalDate DESDE = LocalDate.of(2026, 8, 23);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 2);

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private CotizacionRepository cotizacionRepository;

    @Autowired
    private CotizacionDetalleRepository cotizacionDetalleRepository;

    @Autowired
    private OrdenCompraRepository ordenCompraRepository;

    @Autowired
    private FacturacionRepository facturacionRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TerceroRepository terceroRepository;

    @Autowired
    private TipoDocumentoRepository tipoDocumentoRepository;

    @Autowired
    private DistritoRepository distritoRepository;

    @Autowired
    private ProvinciaRepository provinciaRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private PaisRepository paisRepository;

    @Autowired
    private SectorEconomicoRepository sectorEconomicoRepository;

    @Autowired
    private MonedaRepository monedaRepository;

    @Autowired
    private CondicionPagoRepository condicionPagoRepository;

    @Autowired
    private EstadoCotizacionRepository estadoCotizacionRepository;

    @Autowired
    private EstadoOrdenCompraRepository estadoOrdenCompraRepository;

    @Autowired
    private EstadoFacturacionRepository estadoFacturacionRepository;

    @Autowired
    private TipoComprobanteRepository tipoComprobanteRepository;

    @Autowired
    private UnidadMedidaRepository unidadMedidaRepository;

    @Autowired
    private CategoriaProductoRepository categoriaProductoRepository;

    @Autowired
    private CategoriaServicioRepository categoriaServicioRepository;

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Cliente cliente;
    private Usuario vendedor;
    private Moneda moneda;
    private CondicionPago condicion;
    private EstadoCotizacion estadoCotizacion;
    private EstadoOrdenCompra estadoOrdenCompra;
    private EstadoFacturacion estadoFacturacion;
    private TipoComprobante tipoComprobante;
    private UnidadMedida unidad;
    private CategoriaProducto categoriaProducto;
    private CategoriaServicio categoriaServicio;
    private Marca marca;
    private SectorEconomico sector;

    @BeforeEach
    void seed() {
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso("PER");
            e.setNombrePais("Perú");
        }));
        Departamento departamento = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Lima");
        }));
        Provincia provincia = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia("Lima");
        }));
        Distrito distrito = distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo("150101");
            e.setNombreDistrito("Lima");
        }));
        TipoDocumento tipoDocumento = tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
            e.setCodigoTipo("RUC");
            e.setNombreTipo("RUC");
            e.setLongitudDocumento((short) 11);
            e.setSoloNumerico(true);
        }));
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipoDocumento);
            e.setDistrito(distrito);
            e.setNumeroDocumento("20505500001");
            e.setRazonSocial("ACME S.A.C.");
        }));
        sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> {
            e.setNombreSector("Minería");
        }));
        cliente = clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
        moneda = monedaRepository.save(auditar(new Moneda(), e -> {
            e.setCodigoMoneda("PEN");
            e.setNombreMoneda("Sol peruano");
            e.setSimbolo("S/");
        }));
        condicion = condicionPagoRepository.save(auditar(new CondicionPago(), e -> {
            e.setNombreCondicion("CONTADO");
            e.setDiasCredito((short) 0);
        }));
        estadoCotizacion = estadoCotizacionRepository.save(auditar(new EstadoCotizacion(), e -> {
            e.setCodigoEstado("BORRADOR");
            e.setNombreEstado("Borrador");
            e.setOrdenFlujo((short) 1);
            e.setEsFinal(false);
        }));
        estadoOrdenCompra = estadoOrdenCompraRepository.save(auditar(new EstadoOrdenCompra(), e -> {
            e.setCodigoEstado("EN_PROCESO");
            e.setNombreEstado("En proceso");
            e.setEsFinal(false);
        }));
        estadoFacturacion = estadoFacturacionRepository.save(auditar(new EstadoFacturacion(), e -> {
            e.setCodigoEstado("PENDIENTE");
            e.setNombreEstado("Pendiente");
            e.setEsFinal(false);
        }));
        tipoComprobante = tipoComprobanteRepository.save(auditar(new TipoComprobante(), e -> {
            e.setCodigoTipo("F001");
            e.setNombreTipo("Factura");
            e.setSeriePrefijo("F");
            e.setRequiereRuc(true);
        }));
        unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("UND");
            e.setNombreUnidad("Unidad");
        }));
        categoriaProducto = categoriaProductoRepository.save(auditar(new CategoriaProducto(), e -> {
            e.setNombreCategoria("General");
            e.setSlugWeb("general-reporte");
            e.setNivel((short) 1);
        }));
        categoriaServicio = categoriaServicioRepository.save(auditar(new CategoriaServicio(), e -> {
            e.setNombreCategoria("Mantenimiento");
            e.setSlugWeb("mantenimiento-reporte");
        }));
        marca = marcaRepository.save(auditar(new Marca(), e -> {
            e.setPais(pais);
            e.setNombreMarca("MARCA-A");
        }));
        Rol rol = rolRepository.save(auditar(new Rol(), e -> {
            e.setNombreRol("VENDEDOR");
            e.setDescripcion("Vendedor");
        }));
        vendedor = usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setNombreUsuario("vendedor");
            e.setNombres("Vendedor");
            e.setCorreo("vendedor@genlogs.pe");
            e.setPasswordHash("{noop}demo");
            e.setIniciales("VD");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));

        // --- Cotizaciones ---
        crearCotizacion("COT-2026-0001", DESDE.plusDays(2), true, 2, "100");
        crearCotizacion("COT-2026-0002", DESDE.minusDays(10), true, 1, "50");
        crearCotizacion("COT-2026-0003", DESDE.plusDays(5), false, 1, "50");

        // --- Orden de compra (dentro del periodo). Su cotización es de fuera
        // del periodo: el reporte de cotizaciones no la cuenta.
        Cotizacion cotizacion = crearCotizacion("COT-2026-0004", DESDE.minusDays(5), true, 1, "10");
        ordenCompraRepository.save(auditar(new OrdenCompra(), e -> {
            e.setCotizacion(cotizacion);
            e.setEstadoOrdenCompra(estadoOrdenCompra);
            e.setCondicionPago(condicion);
            e.setNumeroOrdenCompra("OC-0001");
            e.setFechaRecepcion(DESDE.plusDays(3));
        }));

        // --- Facturación (dentro del periodo) ---
        OrdenCompra orden = ordenCompraRepository.findAll().get(0);
        facturacionRepository.save(auditar(new Facturacion(), e -> {
            e.setOrdenCompra(orden);
            e.setTipoComprobante(tipoComprobante);
            e.setEstadoFacturacion(estadoFacturacion);
            e.setCondicionPago(condicion);
            e.setMoneda(moneda);
            e.setSerieComprobante("F001");
            e.setNumeroComprobante("00000001");
            e.setFechaEmision(DESDE.plusDays(4));
            e.setFechaVencimiento(DESDE.plusDays(34));
            e.setMontoPagado(new BigDecimal("236.00"));
        }));

        // --- Servicios ---
        servicioRepository.save(auditar(new Servicio(), e -> {
            e.setCategoriaServicio(categoriaServicio);
            e.setUnidadMedida(unidad);
            e.setCodigoServicio("SRV-001");
            e.setNombreServicio("Mantenimiento preventivo");
            e.setVisibleWeb(true);
        }));

        // --- Productos: uno SIN marca (marca es opcional en el catálogo) ---
        productoRepository.save(auditar(new Producto(), e -> {
            e.setCategoriaProducto(categoriaProducto);
            e.setUnidadMedida(unidad);
            e.setCodigoProducto("GLB-000001");
            e.setNombreProducto("Producto con marca");
            e.setMarca(marca);
            e.setVisibleWeb(true);
        }));
        productoRepository.save(auditar(new Producto(), e -> {
            e.setCategoriaProducto(categoriaProducto);
            e.setUnidadMedida(unidad);
            e.setCodigoProducto("GLB-000002");
            e.setNombreProducto("Producto a medida");
            e.setMarca(null);
            e.setVisibleWeb(true);
        }));
    }

    // ------------------------------------------------------------------

    @Test
    void preview_cotizaciones_devuelveLosDatosDelPeriodo() {
        ReportePreviewResponse preview = reporteService.preview(
                request(ReporteRequest.TipoReporte.COTIZACIONES, DESDE, HASTA));

        assertEquals(List.of("Código", "Cliente", "Fecha", "Estado", "Moneda", "Total"),
                preview.getEncabezados());
        // Solo la cotización del periodo y activa.
        assertEquals(1, preview.getTotalFilas());
        assertEquals(1, preview.getFilas().size());
        assertFalse(preview.isTruncado());

        List<String> fila = preview.getFilas().get(0);
        assertEquals("COT-2026-0001", fila.get(0));
        assertEquals("ACME S.A.C.", fila.get(1));
        assertEquals("25/08/2026", fila.get(2));
        assertEquals("Borrador", fila.get(3));
        assertEquals("S/", fila.get(4));
        assertEquals("236.00", fila.get(5));
    }

    @Test
    void preview_ordenesCompra_devuelveLosDatosDelPeriodo() {
        ReportePreviewResponse preview = reporteService.preview(
                request(ReporteRequest.TipoReporte.ORDENES_COMPRA, DESDE, HASTA));

        assertEquals(1, preview.getTotalFilas());
        List<String> fila = preview.getFilas().get(0);
        assertEquals("OC-0001", fila.get(0));
        assertEquals("COT-2026-0004", fila.get(1));
        assertEquals("ACME S.A.C.", fila.get(2));
        assertEquals("26/08/2026", fila.get(3));
        assertEquals("En proceso", fila.get(4));
    }

    @Test
    void preview_facturacion_devuelveLosDatosDelPeriodo() {
        ReportePreviewResponse preview = reporteService.preview(
                request(ReporteRequest.TipoReporte.FACTURACION, DESDE, HASTA));

        assertEquals(1, preview.getTotalFilas());
        List<String> fila = preview.getFilas().get(0);
        assertEquals("F001-00000001", fila.get(0));
        assertEquals("ACME S.A.C.", fila.get(1));
        assertEquals("27/08/2026", fila.get(2));
        assertEquals("236.00", fila.get(4));
        assertEquals("Pendiente", fila.get(5));
    }

    @Test
    void preview_servicios_devuelveElCatalogoActivo() {
        ReportePreviewResponse preview = reporteService.preview(
                request(ReporteRequest.TipoReporte.SERVICIOS, DESDE, HASTA));

        assertEquals(1, preview.getTotalFilas());
        assertEquals("SRV-001", preview.getFilas().get(0).get(0));
        assertEquals("Mantenimiento preventivo", preview.getFilas().get(0).get(1));
    }

    @Test
    void preview_productosSinMarca_noRevienta() {
        ReportePreviewResponse preview = reporteService.preview(
                request(ReporteRequest.TipoReporte.PRODUCTOS, DESDE, HASTA));

        assertEquals(2, preview.getTotalFilas());
        List<String> sinMarca = preview.getFilas().stream()
                .filter(f -> "GLB-000002".equals(f.get(0)))
                .findFirst()
                .orElseThrow();
        assertEquals("Producto a medida", sinMarca.get(1));
        // La marca es opcional: no puede quedar un NullPointerException.
        assertEquals("", sinMarca.get(3));
    }

    @Test
    void preview_todosLosTipos_devuelvenRespuestaValida() {
        for (ReporteRequest.TipoReporte tipo : ReporteRequest.TipoReporte.values()) {
            ReportePreviewResponse preview = reporteService.preview(request(tipo, DESDE, HASTA));
            assertNotNull(preview.getEncabezados(), "Sin encabezados para " + tipo);
            assertFalse(preview.getEncabezados().isEmpty(), "Encabezados vacíos para " + tipo);
            assertEquals(preview.getFilas().size(), preview.getFilasMostradas(), "Filas mostradas para " + tipo);
        }
    }

    @Test
    void preview_periodoSinResultados_devuelveCeroFilasYNoFalla() {
        ReportePreviewResponse preview = reporteService.preview(request(
                ReporteRequest.TipoReporte.COTIZACIONES, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31)));

        assertEquals(0, preview.getTotalFilas());
        assertEquals(0, preview.getFilasMostradas());
        assertTrue(preview.getFilas().isEmpty());
        assertFalse(preview.isTruncado());
        assertNotNull(preview.getEncabezados());
    }

    @Test
    void preview_fechasInvalidas_lanzaErrorDeNegocio() {
        BusinessException e = assertThrows(BusinessException.class, () -> reporteService.preview(
                request(ReporteRequest.TipoReporte.COTIZACIONES, HASTA, DESDE)));
        assertTrue(e.getMessage().contains("no puede ser mayor"));

        assertThrows(BusinessException.class, () -> reporteService.preview(
                request(ReporteRequest.TipoReporte.COTIZACIONES, null, HASTA)));
        assertThrows(BusinessException.class, () -> reporteService.preview(
                request(ReporteRequest.TipoReporte.COTIZACIONES, DESDE, null)));
    }

    @Test
    void preview_tipoDeReporteDesconocido_lanzaErrorDeNegocio() {
        ReporteRequest request = request(ReporteRequest.TipoReporte.COTIZACIONES, DESDE, HASTA);
        request.setTipoReporte(null);
        assertThrows(BusinessException.class, () -> reporteService.preview(request));
    }

    @Test
    void generar_sigueFuncionandoYTraeLasMismasFilasQueLaVistaPrevia() throws Exception {
        ReporteRequest request = request(ReporteRequest.TipoReporte.COTIZACIONES, DESDE, HASTA);
        request.setFormato(ReporteRequest.FormatoReporte.EXCEL);

        ReporteService.ArchivoGenerado archivo = reporteService.generar(request);
        assertNotNull(archivo.contenido());
        assertTrue(archivo.nombreArchivo().endsWith(".xlsx"));
        // Firma de un .xlsx (ZIP)
        assertEquals('P', (char) archivo.contenido()[0]);
        assertEquals('K', (char) archivo.contenido()[1]);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            int filasDelArchivo = libro.getSheetAt(0).getLastRowNum(); // incluye la de encabezados
            ReportePreviewResponse preview = reporteService.preview(request);
            // Mismas reglas de negocio: preview y archivo traen las mismas filas.
            assertEquals(preview.getTotalFilas(), filasDelArchivo);
        }
    }

    @Test
    void generar_pdf_sigueFuncionando() {
        ReporteRequest request = request(ReporteRequest.TipoReporte.COTIZACIONES, DESDE, HASTA);
        request.setFormato(ReporteRequest.FormatoReporte.PDF);

        ReporteService.ArchivoGenerado archivo = reporteService.generar(request);
        assertNotNull(archivo.contenido());
        assertTrue(archivo.nombreArchivo().endsWith(".pdf"));
        assertEquals("%PDF", new String(archivo.contenido(), 0, 4));
    }

    @Test
    void preview_recortaLasFilasYInformaElTotalReal() {
        // 2 productos del seed + 199 más = 201 (> tope de 200 de la vista previa)
        for (int i = 3; i <= 201; i++) {
            final int n = i;
            productoRepository.save(auditar(new Producto(), e -> {
                e.setCategoriaProducto(categoriaProducto);
                e.setUnidadMedida(unidad);
                e.setCodigoProducto(String.format("GLB-%06d", n));
                e.setNombreProducto("Producto " + n);
                e.setVisibleWeb(true);
            }));
        }

        ReportePreviewResponse preview = reporteService.preview(
                request(ReporteRequest.TipoReporte.PRODUCTOS, DESDE, HASTA));

        assertEquals(201, preview.getTotalFilas());
        assertEquals(200, preview.getFilasMostradas());
        assertTrue(preview.isTruncado());
    }

    // ------------------------------------------------------------------

    private ReporteRequest request(ReporteRequest.TipoReporte tipo, LocalDate desde, LocalDate hasta) {
        ReporteRequest request = new ReporteRequest();
        request.setTipoReporte(tipo);
        request.setFechaInicio(desde);
        request.setFechaFin(hasta);
        request.setFormato(ReporteRequest.FormatoReporte.PDF);
        return request;
    }

    private Cotizacion crearCotizacion(String codigo, LocalDate fecha, boolean activa,
                                        int cantidad, String precio) {
        Cotizacion cotizacion = cotizacionRepository.save(auditar(new Cotizacion(), e -> {
            e.setCodigoCotizacion(codigo);
            e.setCliente(cliente);
            e.setFechaCotizacion(fecha);
            e.setFechaValidez(fecha.plusDays(15));
            e.setUsuarioVendedor(vendedor);
            e.setEstadoCotizacion(estadoCotizacion);
            e.setMoneda(moneda);
            e.setCondicionPago(condicion);
            e.setSectorEconomico(sector);
        }));
        if (!activa) {
            cotizacion.setStatus("I");
            cotizacion = cotizacionRepository.save(cotizacion);
        }
        final Cotizacion cotizacionFinal = cotizacion;
        cotizacionDetalleRepository.save(auditar(new CotizacionDetalle(), e -> {
            e.setCotizacion(cotizacionFinal);
            e.setUnidadMedida(unidad);
            e.setDescripcionPersonalizada("Linea de prueba");
            e.setCantidad(new BigDecimal(cantidad));
            e.setPrecioUnitario(new BigDecimal(precio));
            e.setDescuentoUnitario(BigDecimal.ZERO);
        }));
        return cotizacion;
    }

    private <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        return entidad;
    }
}
