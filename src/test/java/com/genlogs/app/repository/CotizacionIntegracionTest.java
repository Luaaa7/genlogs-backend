package com.genlogs.app.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.service.CotizacionService;

/**
 * Integración real del módulo de cotizaciones contra H2 (sin mocks): valida el
 * JPQL de CotizacionRepository.buscarPaginado y las consultas nativas a
 * vw_cotizacion_totales, que son justo lo que provocaba el HTTP 500 del
 * listado (y el N+1 de totales por fila).
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Transactional
class CotizacionIntegracionTest {

    @Autowired
    private CotizacionService cotizacionService;

    @Autowired
    private CotizacionRepository cotizacionRepository;

    @Autowired
    private CotizacionDetalleRepository cotizacionDetalleRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TerceroRepository terceroRepository;

    @Autowired
    private SectorEconomicoRepository sectorEconomicoRepository;

    @Autowired
    private PaisRepository paisRepository;

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @Autowired
    private ProvinciaRepository provinciaRepository;

    @Autowired
    private DistritoRepository distritoRepository;

    @Autowired
    private TipoDocumentoRepository tipoDocumentoRepository;

    @Autowired
    private MonedaRepository monedaRepository;

    @Autowired
    private CondicionPagoRepository condicionPagoRepository;

    @Autowired
    private EstadoCotizacionRepository estadoCotizacionRepository;

    @Autowired
    private UnidadMedidaRepository unidadMedidaRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void listar_devuelveLosTotalesDeLaPaginaYLosAliasDelFrontend() {
        Cotizacion cotizacion = crearCotizacion("ACME S.A.C.", "COT-2026-0001");

        Pageable pageable = PageRequest.of(0, 10, Sort.by("fechaCotizacion").descending());
        PaginaResponse<CotizacionResponse> pagina =
                cotizacionService.listar(null, null, null, pageable);

        assertEquals(1, pagina.getContent().size());
        CotizacionResponse c = pagina.getContent().get(0);

        assertEquals(cotizacion.getIdCotizacion(), c.getIdCotizacion());
        assertEquals("COT-2026-0001", c.getCodigoCotizacion());
        assertEquals("ACME S.A.C.", c.getClienteNombre());
        assertEquals("BORRADOR", c.getEstadoCodigo());
        assertEquals("BORRADOR", c.getEstadoCotizacion());
        // "moneda" es el CÓDIGO (el frontend lo pasa a Intl.NumberFormat).
        assertEquals("PEN", c.getMoneda());
        assertEquals("PEN", c.getMonedaCodigo());
        assertEquals("S/", c.getMonedaSimbolo());
        assertEquals(0, pagina.getCurrentPage());
        assertEquals(10, pagina.getPageSize());
        assertEquals(1, pagina.getTotalElements());

        // Totales calculados en la vista (2 x 100 = 200; IGV 18% = 36).
        assertEquals(0, new BigDecimal("200.00").compareTo(c.getSubtotal()));
        assertEquals(0, new BigDecimal("36.00").compareTo(c.getIgv()));
        assertEquals(0, new BigDecimal("236.00").compareTo(c.getTotal()));
    }

    @Test
    void buscarPorId_devuelveDetalleConLineasYTotales() {
        Cotizacion cotizacion = crearCotizacion("ACME S.A.C.", "COT-2026-0002");

        CotizacionResponse detalle = cotizacionService.buscarPorId(cotizacion.getIdCotizacion());

        assertNotNull(detalle.getDetalles());
        assertEquals(1, detalle.getDetalles().size());
        // Total = subtotal (200) + IGV 18% (36) = 236.
        assertEquals(0, new BigDecimal("236.00").compareTo(detalle.getTotal()));
        // El importe de la línea lo calcula la BD; si no está, el servicio lo
        // recalcula (cantidad x (precio - descuento)).
        assertEquals(0, new BigDecimal("200.00").compareTo(detalle.getDetalles().get(0).getSubtotal()));
    }

    @Test
    void listar_conFiltroDeEstadoYMoneda_filtraEnBaseDeDatos() {
        crearCotizacion("ACME S.A.C.", "COT-2026-0003");

        Pageable pageable = PageRequest.of(0, 10, Sort.by("fechaCotizacion").descending());

        assertEquals(1, cotizacionService.listar("BORRADOR", null, "PEN", pageable).getContent().size());
        assertEquals(0, cotizacionService.listar("APROBADA", null, "PEN", pageable).getContent().size());
        assertEquals(0, cotizacionService.listar("BORRADOR", null, "USD", pageable).getContent().size());
    }

    private Cotizacion crearCotizacion(String razonSocial, String codigo) {
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

        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> {
            e.setNombreSector("Minería " + codigo);
        }));
        // El tercero se crea antes que el cliente (FK id_tercero NOT NULL).
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipoDocumento);
            e.setDistrito(distrito);
            e.setNumeroDocumento("205055" + Math.abs(codigo.hashCode() % 10000));
            e.setRazonSocial(razonSocial);
            e.setCorreo("compras@" + Math.abs(codigo.hashCode() % 10000) + ".pe");
        }));
        Cliente cliente = clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));

        Moneda moneda = monedaRepository.save(auditar(new Moneda(), e -> {
            e.setCodigoMoneda("PEN");
            e.setNombreMoneda("Sol peruano");
            e.setSimbolo("S/");
        }));
        CondicionPago condicion = condicionPagoRepository.save(auditar(new CondicionPago(), e -> {
            e.setNombreCondicion("CONTADO");
            e.setDiasCredito((short) 0);
        }));
        EstadoCotizacion borrador = estadoCotizacionRepository.save(auditar(new EstadoCotizacion(), e -> {
            e.setCodigoEstado("BORRADOR");
            e.setNombreEstado("Borrador");
            e.setOrdenFlujo((short) 1);
            e.setEsFinal(false);
        }));
        UnidadMedida unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("UND");
            e.setNombreUnidad("Unidad");
        }));
        Rol rol = rolRepository.save(auditar(new Rol(), e -> {
            e.setNombreRol("VENDEDOR");
            e.setDescripcion("Vendedor");
        }));
        Usuario vendedor = usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setNombreUsuario("vendedor");
            e.setNombres("Vendedor Demo");
            e.setCorreo("vendedor@genlogs.pe");
            e.setPasswordHash("{noop}demo");
            e.setIniciales("VD");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));

        Cotizacion cotizacion = cotizacionRepository.save(auditar(new Cotizacion(), e -> {
            e.setCodigoCotizacion(codigo);
            e.setCliente(cliente);
            e.setFechaCotizacion(LocalDate.now());
            e.setFechaValidez(LocalDate.now().plusDays(15));
            e.setUsuarioVendedor(vendedor);
            e.setEstadoCotizacion(borrador);
            e.setMoneda(moneda);
            e.setCondicionPago(condicion);
            e.setSectorEconomico(sector);
            e.setObservaciones("Cotización de prueba");
        }));

        cotizacionDetalleRepository.save(auditar(new CotizacionDetalle(), e -> {
            e.setCotizacion(cotizacion);
            e.setUnidadMedida(unidad);
            e.setDescripcionPersonalizada("Placa de desgaste");
            e.setCantidad(new BigDecimal("2"));
            e.setPrecioUnitario(new BigDecimal("100"));
            e.setDescuentoUnitario(BigDecimal.ZERO);
        }));

        return cotizacion;
    }

    /** Los campos de auditoría son NOT NULL en la BD. */
    private <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        return entidad;
    }
}
