package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.genlogs.app.controller.CotizacionController;
import com.genlogs.app.controller.PortalCotizacionController;
import com.genlogs.app.dto.CambiarEstadoCotizacionRequest;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.EstadoOrdenCompra;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Notificacion;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.EstadoOrdenCompraRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

/**
 * La aprobación se confirma sola. La orden corre después, en su transacción:
 * si falla, la cotización sigue APROBADA. Sin {@code @Transactional} en la
 * clase, para que el commit sea real y no el de un test que al final revierte.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql("/schema.sql")
public class AprobacionOrdenIndependienteTest {

    private static final AtomicInteger SERIE = new AtomicInteger(20);

    @Autowired private PortalCotizacionController portal;
    @Autowired private CotizacionController cotizaciones;
    @Autowired private OrdenCompraService ordenCompraService;
    @Autowired private CotizacionRepository cotizacionRepository;
    @Autowired private CotizacionDetalleRepository cotizacionDetalleRepository;
    @Autowired private OrdenCompraRepository ordenCompraRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TerceroRepository terceroRepository;
    @Autowired private SectorEconomicoRepository sectorEconomicoRepository;
    @Autowired private PaisRepository paisRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ProvinciaRepository provinciaRepository;
    @Autowired private DistritoRepository distritoRepository;
    @Autowired private TipoDocumentoRepository tipoDocumentoRepository;
    @Autowired private MonedaRepository monedaRepository;
    @Autowired private CondicionPagoRepository condicionPagoRepository;
    @Autowired private EstadoCotizacionRepository estadoCotizacionRepository;
    @Autowired private EstadoOrdenCompraRepository estadoOrdenCompraRepository;
    @Autowired private UnidadMedidaRepository unidadMedidaRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean
    private NotificacionService notificacionService;

    private Rastro rastro;

    @BeforeEach
    void aliasDeAuditoria() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createNativeQuery("""
                        CREATE ALIAS IF NOT EXISTS SET_CONFIG AS $$
                        String setConfig(String key, String value, boolean local) {
                            return value;
                        }
                        $$
                        """)
                .executeUpdate());
    }

    @AfterEach
    void limpiar() {
        reset(notificacionService);
        SecurityContextHolder.clearContext();
        if (rastro == null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            borrar(rastro.cotizacion);
            borrarCatalogo(rastro);
            entityManager.flush();
            entityManager.clear();
        });
        rastro = null;
    }

    @Test
    void siLaOrdenFallaEnElPortalLaCotizacionSigueAprobada() {
        Long id = sembrar(false);
        sembrarDestinatarios();
        entrar(rastro.nombreUsuario, RolSistema.CLIENTE.name());

        var respuesta = portal.aprobar(id, null);

        assertEquals("Aprobada", respuesta.getBody().getEstado());
        assertEquals("APROBADA", codigoDe(id));
        assertEquals(0, ordenesDe(id));
        assertAvisoDeFallo(id);
    }

    @Test
    void siLaOrdenFallaAlCambiarEstadoLaCotizacionSigueAprobada() {
        Long id = sembrar(false);
        sembrarDestinatarios();
        entrar(rastro.nombreUsuario, RolSistema.JEFE_ADMINISTRATIVO.name());
        CambiarEstadoCotizacionRequest request = new CambiarEstadoCotizacionRequest();
        request.setCodigoEstado("APROBADA");

        var respuesta = cotizaciones.cambiarEstado(id, request);

        assertEquals("APROBADA", respuesta.getBody().getEstadoCodigo());
        assertEquals("APROBADA", codigoDe(id));
        assertEquals(0, ordenesDe(id));
        assertAvisoDeFallo(id);
    }

    @Test
    void siElAvisoFallaLaAprobacionYElSeguimientoSiguen() {
        Long id = sembrar(false);
        doThrow(new RuntimeException("aviso caido")).when(notificacionService)
                .avisarRol(any(), any(), any(), any(), any(), anyBoolean());
        entrar(rastro.nombreUsuario, RolSistema.CLIENTE.name());

        portal.aprobar(id, null);

        assertEquals("APROBADA", codigoDe(id));
        assertTrue(seguimientoCotizacionRepository.findByCotizacion(id).stream()
                .anyMatch(s -> s.getComentario() != null && s.getComentario().contains("RECIBIDA")));
    }

    @Test
    void dobleAprobacionCreaUnaSolaOrden() {
        Long id = sembrar(true);
        entrar(rastro.nombreUsuario, RolSistema.CLIENTE.name());

        portal.aprobar(id, null);
        assertEquals(1, ordenesDe(id));

        assertThrows(BusinessException.class, () -> portal.aprobar(id, null));
        assertNull(ordenCompraService.crearAutomatica(id));

        assertEquals("APROBADA", codigoDe(id));
        assertEquals(1, ordenesDe(id));
        assertEquals(0, avisos().size());
    }

    private Long sembrar(boolean conRecibida) {
        int n = SERIE.getAndIncrement();
        rastro = new Rastro();
        rastro.nombreUsuario = "cli" + n;

        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("Z%02d", n % 100));
            e.setNombrePais("Pais " + n);
        }));
        rastro.pais = pais.getIdPais();
        Departamento departamento = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Depto " + n);
        }));
        rastro.departamento = departamento.getIdDepartamento();
        Provincia provincia = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia("Prov " + n);
        }));
        rastro.provincia = provincia.getIdProvincia();
        Distrito distrito = distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo(String.format("8%05d", n));
            e.setNombreDistrito("Dist " + n);
        }));
        rastro.distrito = distrito.getIdDistrito();
        TipoDocumento tipoDocumento = tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
            e.setCodigoTipo("T" + n);
            e.setNombreTipo("Doc " + n);
            e.setLongitudDocumento((short) 11);
            e.setSoloNumerico(true);
        }));
        rastro.tipoDocumento = tipoDocumento.getIdTipoDocumento();
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> {
            e.setNombreSector("Sector " + n);
        }));
        rastro.sector = sector.getIdSectorEconomico();
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipoDocumento);
            e.setDistrito(distrito);
            e.setNumeroDocumento(String.format("1%010d", n));
            e.setRazonSocial("Mina " + n);
        }));
        rastro.tercero = tercero.getIdTercero();
        Cliente cliente = clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
        rastro.cliente = cliente.getIdCliente();
        Moneda moneda = monedaRepository.save(auditar(new Moneda(), e -> {
            e.setCodigoMoneda(String.format("U%02d", n % 100));
            e.setNombreMoneda("Moneda " + n);
            e.setSimbolo("u");
        }));
        rastro.moneda = moneda.getIdMoneda();
        CondicionPago condicion = condicionPagoRepository.save(auditar(new CondicionPago(), e -> {
            e.setNombreCondicion("Pago " + n);
            e.setDiasCredito((short) 0);
        }));
        rastro.condicion = condicion.getIdCondicionPago();
        EstadoCotizacion enviada = estadoCotizacionRepository.save(estado("ENVIADA", "Enviada", (short) 2, false));
        EstadoCotizacion aprobada = estadoCotizacionRepository.save(estado("APROBADA", "Aprobada", (short) 4, true));
        rastro.enviada = enviada.getIdEstadoCotizacion();
        rastro.aprobada = aprobada.getIdEstadoCotizacion();
        if (conRecibida) {
            EstadoOrdenCompra recibida = estadoOrdenCompraRepository.save(auditar(new EstadoOrdenCompra(), e -> {
                e.setCodigoEstado("RECIBIDA");
                e.setNombreEstado("Recibida");
                e.setEsFinal(false);
            }));
            rastro.recibida = recibida.getIdEstadoOrdenCompra();
        }
        UnidadMedida unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("U" + n);
            e.setNombreUnidad("Unidad " + n);
        }));
        rastro.unidad = unidad.getIdUnidadMedida();
        Rol rol = rolRepository.save(auditar(new Rol(), e -> {
            e.setNombreRol("ROL" + n);
            e.setDescripcion("Prueba");
        }));
        rastro.rol = rol.getIdRol();
        Usuario usuario = usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario(rastro.nombreUsuario);
            e.setNombres("Cliente Prueba");
            e.setCorreo("cli" + n + "@mina.pe");
            e.setPasswordHash("{noop}demo");
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        rastro.usuario = usuario.getIdUsuario();
        Cotizacion cotizacion = cotizacionRepository.save(auditar(new Cotizacion(), e -> {
            e.setCodigoCotizacion("COT-OC-" + n);
            e.setCliente(cliente);
            e.setFechaCotizacion(LocalDate.now());
            e.setFechaValidez(LocalDate.now().plusDays(10));
            e.setUsuarioVendedor(usuario);
            e.setEstadoCotizacion(enviada);
            e.setMoneda(moneda);
            e.setCondicionPago(condicion);
            e.setSectorEconomico(sector);
        }));
        rastro.cotizacion = cotizacion.getIdCotizacion();
        cotizacionDetalleRepository.save(auditar(new CotizacionDetalle(), e -> {
            e.setCotizacion(cotizacion);
            e.setUnidadMedida(unidad);
            e.setDescripcionPersonalizada("Repuesto");
            e.setCantidad(new BigDecimal("2"));
            e.setPrecioUnitario(new BigDecimal("100"));
            e.setDescuentoUnitario(BigDecimal.ZERO);
        }));
        return cotizacion.getIdCotizacion();
    }

    private void entrar(String nombre, String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                nombre, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private String codigoDe(Long id) {
        return new TransactionTemplate(transactionManager).execute(status -> cotizacionRepository.findById(id)
                .orElseThrow()
                .getEstadoCotizacion()
                .getCodigoEstado());
    }

    private long ordenesDe(Long id) {
        Long total = new TransactionTemplate(transactionManager).execute(status -> ordenCompraRepository.findAll().stream()
                .filter(orden -> id.equals(orden.getCotizacion().getIdCotizacion()))
                .filter(orden -> orden.getStatus() != null && orden.getStatus().startsWith("A"))
                .count());
        return total == null ? 0 : total;
    }

    private void sembrarDestinatarios() {
        sembrarUno(RolSistema.ASISTENTE_ADMINISTRATIVO.name(), "asist", "AAD");
        sembrarUno(RolSistema.JEFE_ADMINISTRATIVO.name(), "jefe", "JAD");
        sembrarUno(RolSistema.GERENTE_GENERAL.name(), "ger", "GGD");
    }

    private void sembrarUno(String rolNombre, String prefijo, String iniciales) {
        int n = Math.floorMod(rastro.nombreUsuario.hashCode(), 100000);
        Rol rol = rolRepository.save(auditar(new Rol(), e -> {
            e.setNombreRol(rolNombre);
            e.setDescripcion(rolNombre);
        }));
        rastro.rolesExtra.add(rol.getIdRol());
        Usuario usuario = usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setNombreUsuario(prefijo + n);
            e.setNombres(prefijo);
            e.setCorreo(prefijo + n + "@genlogs.pe");
            e.setPasswordHash("{noop}demo");
            e.setIniciales(iniciales);
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        rastro.destinatarios.add(usuario.getIdUsuario());
    }

    private void assertAvisoDeFallo(Long id) {
        List<Notificacion> avisos = avisos();
        assertEquals(2, avisos.size());
        assertTrue(avisos.stream().noneMatch(a -> rastro.destinatarios.get(2).equals(a.getIdUsuario())));
        for (Notificacion aviso : avisos) {
            String texto = (aviso.getTitulo() + " " + aviso.getMensaje()).toLowerCase();
            assertFalse(texto.contains("margen"));
            assertFalse(texto.contains("costo"));
            assertTrue(aviso.getMensaje().contains("la orden de compra no se creó y debe registrarse a mano"));
            assertEquals("/cotizaciones/" + id, aviso.getEnlace());
        }
        assertTrue(seguimientoCotizacionRepository.findByCotizacion(id).stream().anyMatch(this::esFalloDeOrden));
    }

    private List<Notificacion> avisos() {
        return notificacionRepository.findAll().stream()
                .filter(n -> "ORDEN_NO_CREADA".equals(n.getTipo()))
                .toList();
    }

    private boolean esFalloDeOrden(SeguimientoCotizacion seguimiento) {
        return seguimiento.getComentario() != null
                && seguimiento.getComentario().contains("RECIBIDA")
                && seguimiento.getComentario().contains("la orden de compra no se creó y debe registrarse a mano")
                && !seguimiento.getComentario().toLowerCase().contains("margen")
                && !seguimiento.getComentario().toLowerCase().contains("costo");
    }

    private void borrar(Long idCotizacion) {
        if (idCotizacion == null) {
            return;
        }
        entityManager.createQuery("delete from OrdenCompra o where o.cotizacion.idCotizacion = :id")
                .setParameter("id", idCotizacion).executeUpdate();
        entityManager.createQuery("delete from SeguimientoCotizacion s where s.cotizacion.idCotizacion = :id")
                .setParameter("id", idCotizacion).executeUpdate();
        entityManager.createQuery("delete from CotizacionDetalle d where d.cotizacion.idCotizacion = :id")
                .setParameter("id", idCotizacion).executeUpdate();
        entityManager.createQuery("delete from Cotizacion c where c.idCotizacion = :id")
                .setParameter("id", idCotizacion).executeUpdate();
    }

    private void borrarCatalogo(Rastro datos) {
        for (Long idUsuario : datos.destinatarios) {
            borrarUno("delete from Notificacion n where n.idUsuario = :id", idUsuario);
        }
        for (Long idUsuario : datos.destinatarios) {
            borrarUno("delete from Usuario u where u.idUsuario = :id", idUsuario);
        }
        for (Integer idRol : datos.rolesExtra) {
            borrarUno("delete from Rol r where r.idRol = :id", idRol);
        }
        borrarUno("delete from Usuario u where u.idUsuario = :id", datos.usuario);
        borrarUno("delete from Cliente c where c.idCliente = :id", datos.cliente);
        borrarUno("delete from Tercero t where t.idTercero = :id", datos.tercero);
        borrarUno("delete from SectorEconomico s where s.idSectorEconomico = :id", datos.sector);
        borrarUno("delete from CondicionPago c where c.idCondicionPago = :id", datos.condicion);
        borrarUno("delete from Moneda m where m.idMoneda = :id", datos.moneda);
        borrarUno("delete from EstadoCotizacion e where e.idEstadoCotizacion = :id", datos.enviada);
        borrarUno("delete from EstadoCotizacion e where e.idEstadoCotizacion = :id", datos.aprobada);
        borrarUno("delete from EstadoOrdenCompra e where e.idEstadoOrdenCompra = :id", datos.recibida);
        borrarUno("delete from UnidadMedida u where u.idUnidadMedida = :id", datos.unidad);
        borrarUno("delete from Rol r where r.idRol = :id", datos.rol);
        borrarUno("delete from Distrito d where d.idDistrito = :id", datos.distrito);
        borrarUno("delete from Provincia p where p.idProvincia = :id", datos.provincia);
        borrarUno("delete from Departamento d where d.idDepartamento = :id", datos.departamento);
        borrarUno("delete from Pais p where p.idPais = :id", datos.pais);
        borrarUno("delete from TipoDocumento t where t.idTipoDocumento = :id", datos.tipoDocumento);
    }

    private void borrarUno(String jpql, Object id) {
        if (id != null) {
            entityManager.createQuery(jpql).setParameter("id", id).executeUpdate();
        }
    }

    private EstadoCotizacion estado(String codigo, String nombre, short orden, boolean fin) {
        return auditar(new EstadoCotizacion(), e -> {
            e.setCodigoEstado(codigo);
            e.setNombreEstado(nombre);
            e.setOrdenFlujo(orden);
            e.setEsFinal(fin);
        });
    }

    private <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        return entidad;
    }

    private static final class Rastro {
        private String nombreUsuario;
        private Long cotizacion;
        private Long usuario;
        private Long cliente;
        private Long tercero;
        private Integer sector;
        private Integer condicion;
        private Integer moneda;
        private Integer enviada;
        private Integer aprobada;
        private Integer recibida;
        private Integer unidad;
        private Integer rol;
        private Integer distrito;
        private Integer provincia;
        private Integer departamento;
        private Integer pais;
        private Integer tipoDocumento;
        private final List<Long> destinatarios = new ArrayList<>();
        private final List<Integer> rolesExtra = new ArrayList<>();
    }
}
