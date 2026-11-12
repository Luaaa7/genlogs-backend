package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UsuarioRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VencimientoCotizacionesIntegracionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 4, 20);

    @Autowired private VencimientoCotizacionesService servicio;
    @Autowired private CotizacionRepository cotizaciones;
    @Autowired private SeguimientoCotizacionRepository seguimientos;
    @Autowired private EstadoCotizacionRepository estados;
    @Autowired private ClienteRepository clientes;
    @Autowired private TerceroRepository terceros;
    @Autowired private SectorEconomicoRepository sectores;
    @Autowired private PaisRepository paises;
    @Autowired private DepartamentoRepository departamentos;
    @Autowired private ProvinciaRepository provincias;
    @Autowired private DistritoRepository distritos;
    @Autowired private TipoDocumentoRepository tiposDocumento;
    @Autowired private MonedaRepository monedas;
    @Autowired private CondicionPagoRepository condiciones;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRepository usuarios;

    @Test
    void venceALosDieciseisDiasYLaSegundaCorridaNoRepiteNada() {
        String marca = String.valueOf(System.nanoTime());
        Catalogo catalogo = catalogo(marca);
        LocalDate hace16 = HOY.minusDays(16);
        LocalDate hace15 = HOY.minusDays(15);
        LocalDate hace14 = HOY.minusDays(14);

        Cotizacion enviada = cotizacion(catalogo, "E16-" + marca, "ENVIADA", hace16);
        Cotizacion negociacion = cotizacion(catalogo, "N16-" + marca, "EN_NEGOCIACION", hace16);
        Cotizacion quince = cotizacion(catalogo, "E15-" + marca, "ENVIADA", hace15);
        Cotizacion catorce = cotizacion(catalogo, "E14-" + marca, "ENVIADA", hace14);
        Cotizacion aprobada = cotizacion(catalogo, "AP-" + marca, "APROBADA", hace16);
        Cotizacion borrador = cotizacion(catalogo, "BO-" + marca, "BORRADOR", hace16);
        Cotizacion pendiente = cotizacion(catalogo, "PE-" + marca, "PENDIENTE_APROBACION", hace16);
        Cotizacion interna = cotizacion(catalogo, "AI-" + marca, "APROBADA_INTERNA", hace16);
        Cotizacion rechazada = cotizacion(catalogo, "RE-" + marca, "RECHAZADA", hace16);
        Cotizacion anulada = cotizacion(catalogo, "AN-" + marca, "ANULADA", hace16);

        assertEquals(2, servicio.vencer(HOY));
        assertEquals(0, servicio.vencer(HOY));

        assertVencida(enviada);
        assertVencida(negociacion);
        assertEquals("ENVIADA", quince.getEstadoCotizacion().getCodigoEstado());
        assertEquals("ENVIADA", catorce.getEstadoCotizacion().getCodigoEstado());
        assertEquals("APROBADA", aprobada.getEstadoCotizacion().getCodigoEstado());
        assertEquals("BORRADOR", borrador.getEstadoCotizacion().getCodigoEstado());
        assertEquals("PENDIENTE_APROBACION", pendiente.getEstadoCotizacion().getCodigoEstado());
        assertEquals("APROBADA_INTERNA", interna.getEstadoCotizacion().getCodigoEstado());
        assertEquals("RECHAZADA", rechazada.getEstadoCotizacion().getCodigoEstado());
        assertEquals("ANULADA", anulada.getEstadoCotizacion().getCodigoEstado());
        assertNull(quince.getUserUpdate());
        assertNull(aprobada.getProcessUpdate());
    }

    private void assertVencida(Cotizacion cotizacion) {
        assertEquals("VENCIDA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertEquals(VencimientoCotizacionesService.USUARIO, cotizacion.getUserUpdate());
        assertEquals(VencimientoCotizacionesService.PROCESO, cotizacion.getProcessUpdate());
        assertNotNull(cotizacion.getDateUpdate());
        var filas = seguimientos.findByCotizacion(cotizacion.getIdCotizacion());
        assertEquals(1, filas.size());
        SeguimientoCotizacion seguimiento = filas.get(0);
        assertNull(seguimiento.getUsuario());
        assertEquals(VencimientoCotizacionesService.USUARIO, seguimiento.getUserCreate());
        assertEquals(VencimientoCotizacionesService.PROCESO, seguimiento.getProcessCreate());
        assertEquals(VencimientoCotizacionesService.COMENTARIO, seguimiento.getComentario());
        assertEquals("VENCIDA", seguimiento.getEstadoCotizacion().getCodigoEstado());
    }

    private Cotizacion cotizacion(Catalogo catalogo, String codigo, String estado, LocalDate fecha) {
        return cotizaciones.save(auditar(new Cotizacion(), cotizacion -> {
            cotizacion.setCodigoCotizacion(codigo);
            cotizacion.setCliente(catalogo.cliente);
            cotizacion.setFechaCotizacion(fecha);
            cotizacion.setFechaValidez(fecha.plusDays(15));
            cotizacion.setUsuarioVendedor(catalogo.vendedor);
            cotizacion.setEstadoCotizacion(catalogo.estados.get(estado));
            cotizacion.setMoneda(catalogo.moneda);
            cotizacion.setCondicionPago(catalogo.condicion);
            cotizacion.setSectorEconomico(catalogo.sector);
            cotizacion.setPorcentajeIgv(new BigDecimal("18.00"));
        }));
    }

    private Catalogo catalogo(String marca) {
        String sufijo = marca.substring(Math.max(0, marca.length() - 6));
        Pais pais = paises.save(auditar(new Pais(), e -> {
            e.setCodigoIso(sufijo.substring(0, 3).toUpperCase());
            e.setNombrePais("Pais " + sufijo);
        }));
        Departamento departamento = departamentos.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Dep " + sufijo);
        }));
        Provincia provincia = provincias.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia("Prov " + sufijo);
        }));
        Distrito distrito = distritos.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo(sufijo);
            e.setNombreDistrito("Dist " + sufijo);
        }));
        TipoDocumento tipo = tiposDocumento.save(auditar(new TipoDocumento(), e -> {
            e.setCodigoTipo("T" + sufijo.substring(0, Math.min(4, sufijo.length())));
            e.setNombreTipo("Doc " + sufijo);
            e.setLongitudDocumento((short) 11);
            e.setSoloNumerico(true);
        }));
        SectorEconomico sector = sectores.save(auditar(new SectorEconomico(), e -> e.setNombreSector("Sector " + sufijo)));
        Tercero tercero = terceros.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipo);
            e.setDistrito(distrito);
            e.setNumeroDocumento("20" + sufijo);
            e.setRazonSocial("Minera " + sufijo);
            e.setCorreo("c" + sufijo + "@genlogs.pe");
        }));
        Cliente cliente = clientes.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
        Moneda moneda = monedas.save(auditar(new Moneda(), e -> {
            e.setCodigoMoneda(sufijo.substring(0, 3).toUpperCase());
            e.setNombreMoneda("Moneda " + sufijo);
            e.setSimbolo("S/");
        }));
        CondicionPago condicion = condiciones.save(auditar(new CondicionPago(), e -> {
            e.setNombreCondicion("CONTADO " + sufijo);
            e.setDiasCredito((short) 0);
        }));
        Rol rol = roles.save(auditar(new Rol(), e -> {
            e.setNombreRol("ROL_" + sufijo);
            e.setDescripcion("Prueba");
        }));
        Usuario vendedor = usuarios.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setNombreUsuario("u" + sufijo);
            e.setNombres("Vendedor");
            e.setCorreo("u" + sufijo + "@genlogs.pe");
            e.setPasswordHash("{noop}demo");
            e.setIniciales("VD");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        Map<String, EstadoCotizacion> porCodigo = new HashMap<>();
        String[][] filas = {
                {"BORRADOR", "1", "false"},
                {"ENVIADA", "2", "false"},
                {"EN_NEGOCIACION", "3", "false"},
                {"APROBADA", "4", "false"},
                {"RECHAZADA", "5", "true"},
                {"VENCIDA", "6", "true"},
                {"ANULADA", "7", "true"},
                {"PENDIENTE_APROBACION", "8", "false"},
                {"APROBADA_INTERNA", "9", "false"}
        };
        for (String[] fila : filas) {
            porCodigo.put(fila[0], estados.save(auditar(new EstadoCotizacion(), e -> {
                e.setCodigoEstado(fila[0]);
                e.setNombreEstado(fila[0]);
                e.setOrdenFlujo(Short.valueOf(fila[1]));
                e.setEsFinal(Boolean.valueOf(fila[2]));
            })));
        }
        return new Catalogo(cliente, sector, moneda, condicion, vendedor, porCodigo);
    }

    private static <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        entidad.setStatus("A");
        return entidad;
    }

    private record Catalogo(Cliente cliente, SectorEconomico sector, Moneda moneda, CondicionPago condicion,
            Usuario vendedor, Map<String, EstadoCotizacion> estados) {
    }
}
