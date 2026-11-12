package com.genlogs.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.service.FactilizaService;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EmpresaMinera;
import com.genlogs.app.model.EtapaComercialMinera;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.ProductoImagen;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.TipoOperacionMinera;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.EmpresaMineraRepository;
import com.genlogs.app.repository.EtapaComercialMineraRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProductoImagenRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.TipoOperacionMineraRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortalClienteTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(200);
    private static final String MENSAJE_REGISTRO =
            "Registro completado. Ya puedes usar el portal.";

    private static final Pattern INICIALES = Pattern.compile("^[A-Z]{2,3}$");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TerceroRepository terceroRepository;
    @Autowired private SectorEconomicoRepository sectorEconomicoRepository;
    @Autowired private PaisRepository paisRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ProvinciaRepository provinciaRepository;
    @Autowired private DistritoRepository distritoRepository;
    @Autowired private TipoDocumentoRepository tipoDocumentoRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoImagenRepository productoImagenRepository;
    @Autowired private CategoriaProductoRepository categoriaProductoRepository;
    @Autowired private UnidadMedidaRepository unidadMedidaRepository;
    @Autowired private EmpresaMineraRepository empresaMineraRepository;
    @Autowired private EtapaComercialMineraRepository etapaComercialMineraRepository;
    @Autowired private TipoOperacionMineraRepository tipoOperacionMineraRepository;
    /** El alta de una minera nueva consulta el RUC en Factiliza; en las pruebas se simula. */
    @MockitoBean private FactilizaService factilizaService;

    @Test
    void catalogoYCoberturaSonPublicosYNoTraenDatosInternos() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito distrito = ubigeo("Arequipa", "Arequipa", "Cayma", n);
        Cliente cliente = cliente(distrito, "2060" + String.format("%07d", n), "Minera Sur " + n, "Sector " + n);
        UnidadMedida unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("U" + n);
            e.setNombreUnidad("Unidad " + n);
        }));
        CategoriaProducto categoria = categoriaProductoRepository.save(auditar(new CategoriaProducto(), e -> {
            e.setNombreCategoria("Fajas " + n);
            e.setSlugWeb("fajas-" + n);
            e.setNivel((short) 1);
        }));
        Producto visible = productoRepository.save(auditar(new Producto(), e -> {
            e.setCategoriaProducto(categoria);
            e.setUnidadMedida(unidad);
            e.setCodigoProducto("PUB-" + n);
            e.setNombreProducto("Faja transportadora " + n);
            e.setDescripcion("Faja para planta");
            e.setVisibleWeb(true);
        }));
        productoRepository.save(auditar(new Producto(), e -> {
            e.setCategoriaProducto(categoria);
            e.setUnidadMedida(unidad);
            e.setCodigoProducto("HID-" + n);
            e.setNombreProducto("Oculto " + n);
            e.setVisibleWeb(false);
        }));
        productoImagenRepository.save(auditar(new ProductoImagen(), e -> {
            e.setProducto(visible);
            e.setUrlImagen("https://cdn.ejemplo/faja-" + n + ".jpg");
            e.setEsPrincipal(true);
        }));
        EtapaComercialMinera etapa = etapaComercialMineraRepository.save(auditar(new EtapaComercialMinera(), e -> {
            e.setNombreEtapa("Produccion " + n);
            e.setOrdenFlujo((short) 1);
        }));
        TipoOperacionMinera tipo = tipoOperacionMineraRepository.save(auditar(new TipoOperacionMinera(), e -> {
            e.setNombreOperacion("Tajo " + n);
        }));
        empresaMineraRepository.save(auditar(new EmpresaMinera(), e -> {
            e.setCliente(cliente);
            e.setEtapaComercial(etapa);
            e.setTipoOperacion(tipo);
            e.setNombreUnidadMinera("Unidad Cayma " + n);
            e.setLatitud(new BigDecimal("-16.400000"));
            e.setLongitud(new BigDecimal("-71.500000"));
            e.setDescripcion("NOTA INTERNA DE COSTO");
        }));

        mockMvc.perform(get("/api/public/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Faja transportadora " + n + "')].categoria").value(hasItem("Fajas " + n)))
                .andExpect(jsonPath("$[?(@.nombre == 'Faja transportadora " + n + "')].unidad").value(hasItem("Unidad " + n)))
                .andExpect(jsonPath("$[?(@.nombre == 'Faja transportadora " + n + "')].imagenUrl")
                        .value(hasItem("https://cdn.ejemplo/faja-" + n + ".jpg")))
                .andExpect(jsonPath("$[?(@.nombre == 'Oculto " + n + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.precio)]").isEmpty())
                .andExpect(jsonPath("$[?(@.proveedor)]").isEmpty());

        mockMvc.perform(get("/api/public/cobertura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Unidad Cayma " + n + "')].region").value(hasItem("Arequipa")))
                .andExpect(jsonPath("$[?(@.nombre == 'Unidad Cayma " + n + "')].descripcion").value(hasItem("Cayma, Arequipa")))
                .andExpect(jsonPath("$[?(@.latitud)]").isEmpty())
                .andExpect(jsonPath("$[?(@.descripcion == 'NOTA INTERNA DE COSTO')]").isEmpty());
    }

    @Test
    void registroDaAccesoInmediatoYPermiteVariosUsuariosPorEmpresa() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String documento = "2055" + String.format("%07d", n);
        Distrito distrito = ubigeo("Lima", "Lima", "Miraflores", n);
        Cliente cliente = cliente(distrito, documento, "Cliente Portal " + n, "Comercio " + n);
        rolCliente();
        String cuerpo = """
                {"razonSocial":"Otra razon","documento":"%s","nombreContacto":"Ana Ruiz",
                 "correo":"ana%s@portal.pe","telefono":"999","password":"clave1234"}
                """.formatted(documento, n);

        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_REGISTRO))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.nombre").value("Ana Ruiz"));

        Usuario creado = usuarioRepository.findByCorreoIgnoreCaseAndStatus("ana" + n + "@portal.pe", "A").orElseThrow();
        assertEquals("CLIENTE", creado.getRol().getNombreRol());
        assertEquals(cliente.getIdCliente(), creado.getCliente().getIdCliente());
        assertTrue(passwordEncoder.matches("clave1234", creado.getPasswordHash()));
        assertNotEquals("clave1234", creado.getPasswordHash());
        assertEquals("Cliente Portal " + n, cliente.getTercero().getRazonSocial());

        // Segundo usuario de la misma empresa (antes lo impedía unique = true y existsByCliente).
        String segundo = """
                {"razonSocial":"Otra razon","documento":"%s","nombreContacto":"Beto Paz",
                 "correo":"beto%s@portal.pe","password":"clave1234"}
                """.formatted(documento, n);
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(segundo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        Usuario otro = usuarioRepository.findByCorreoIgnoreCaseAndStatus("beto" + n + "@portal.pe", "A").orElseThrow();
        assertEquals(cliente.getIdCliente(), otro.getCliente().getIdCliente());

        // El mismo correo no se registra dos veces.
        long antes = usuarioRepository.count();
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ya tiene una cuenta")));
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void registroDeUnaMineraNuevaValidaElRucYCreaElCliente() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String ruc = "2071" + String.format("%07d", n);
        Distrito distrito = ubigeo("Ica", "Nasca", "Nasca", n);
        rolCliente();
        tipoRuc();
        when(factilizaService.consultarPorNumero(ruc)).thenReturn(ConsultaDocumentoResponse.builder()
                .numeroDocumento(ruc)
                .tipoDocumento("RUC")
                .razonSocial("MINERA NUEVA S.A.C.")
                .direccion("Av. Principal 123")
                .idDistrito(distrito.getIdDistrito())
                .origen("FACTILIZA")
                .simulado(false)
                .build());
        String cuerpo = """
                {"razonSocial":"Minera Nueva","documento":"%s","nombreContacto":"Carla Soto",
                 "correo":"carla%s@nueva.pe","password":"clave1234"}
                """.formatted(ruc, n);

        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        Cliente nuevo = clienteRepository.findByNumeroDocumento(ruc).orElseThrow();
        assertEquals("MINERA NUEVA S.A.C.", nuevo.getTercero().getRazonSocial());
        assertEquals("ACTIVO", nuevo.getSituacion());
        assertEquals("Minería", nuevo.getSectorEconomico().getNombreSector());
        Usuario creado = usuarioRepository.findByCorreoIgnoreCaseAndStatus("carla" + n + "@nueva.pe", "A").orElseThrow();
        assertEquals(nuevo.getIdCliente(), creado.getCliente().getIdCliente());
    }

    @Test
    void registroRechazaRucInvalidoONoValidadoSinCrearNada() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        rolCliente();
        String rucMalo = """
                {"razonSocial":"Nadie","documento":"12345678","nombreContacto":"Nadie",
                 "correo":"nadie%s@portal.pe","password":"clave1234"}
                """.formatted(n);
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(rucMalo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.token").doesNotExist());

        String ruc = "2099" + String.format("%07d", n);
        when(factilizaService.consultarPorNumero(ruc)).thenThrow(new BusinessException("Factiliza no encontró datos"));
        String sinSunat = """
                {"razonSocial":"Nadie","documento":"%s","nombreContacto":"Nadie",
                 "correo":"nadie%s@portal.pe","password":"clave1234"}
                """.formatted(ruc, n);
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(sinSunat))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("RUC")));

        assertFalse(usuarioRepository.existsByCorreoIgnoreCase("nadie" + n + "@portal.pe"));
        assertTrue(clienteRepository.findByNumeroDocumento(ruc).isEmpty());
    }

    @Test
    void ingresoDelClienteConCorreoDaTokenDelPortal() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito distrito = ubigeo("Junin", "Huancayo", "El Tambo", n);
        Cliente cliente = cliente(distrito, "2072" + String.format("%07d", n), "Ingreso " + n, "Ingreso " + n);
        usuarioPortal("ingreso." + n, "Ingreso" + n + "@Portal.pe", cliente, rolCliente());

        String respuesta = mockMvc.perform(post("/api/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ingreso" + n + "@portal.pe\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreRol").value("CLIENTE"))
                .andExpect(jsonPath("$.nombreUsuario").value("ingreso." + n))
                .andReturn().getResponse().getContentAsString();
        String token = new ObjectMapper().readTree(respuesta).get("token").asText();

        // El token sirve en el portal (pasa por el filtro JWT real).
        mockMvc.perform(get("/api/portal/pedidos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ingreso" + n + "@portal.pe\",\"password\":\"otra-clave\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void ingresoDelPortalRechazaAlPersonalInterno() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Rol jefe = rolRepository.findByNombreRolIgnoreCase("JEFE_ADMINISTRATIVO")
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol("JEFE_ADMINISTRATIVO");
                    e.setDescripcion("Interno");
                })));
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(jefe);
            e.setNombreUsuario("jefe." + n);
            e.setNombres("Jefe Interno");
            e.setCorreo("jefe" + n + "@genlogs.pe");
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("JI");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));

        mockMvc.perform(post("/api/portal/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"jefe" + n + "@genlogs.pe\",\"password\":\"clave1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void registroCumpleElCheckDeInicialesYNoPisaLosUnicos() throws Exception {
        asegurarCheck("ck_usuario_iniciales",
                "ALTER TABLE usuario ADD CONSTRAINT ck_usuario_iniciales CHECK (iniciales REGEXP '^[A-Z]{2,3}$')");
        asegurarCheck("ck_usuario_status",
                "ALTER TABLE usuario ADD CONSTRAINT ck_usuario_status CHECK (status IN ('A', 'I'))");

        int n = SECUENCIA.incrementAndGet();
        String local = "ana" + n;
        String documento = "2044" + String.format("%07d", n);
        Distrito distrito = ubigeo("Piura", "Piura", "Piura", n);
        cliente(distrito, documento, "Minera Sur " + n, "Cobre " + n);
        Cliente otro = cliente(distrito, "2033" + String.format("%07d", n), "Otro " + n, "Cobre B " + n);
        rolCliente();
        usuarioPortal(local, "ocupado" + n + "@portal.pe", otro, rolCliente());

        String cuerpo = """
                {"razonSocial":"Minera Sur","documento":"%s","nombreContacto":"Ana",
                 "correo":"%s@portal.pe","password":"clave1234"}
                """.formatted(documento, local);
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        Usuario creado = usuarioRepository.findByCorreoIgnoreCaseAndStatus(local + "@portal.pe", "A").orElseThrow();
        assertEquals("MS", creado.getIniciales());
        assertTrue(INICIALES.matcher(creado.getIniciales()).matches());
        assertNotEquals(local, creado.getNombreUsuario());
        assertEquals("Ana", creado.getNombres());
        assertEquals("portal", creado.getUserCreate());
        assertEquals("REGISTRO_PORTAL", creado.getProcessCreate());
        assertEquals("A", creado.getStatus().trim());
        assertNotEquals("clave1234", creado.getPasswordHash());

        long antes = usuarioRepository.count();
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void inicialesCaenEnClCuandoNoHayDosLetras() throws Exception {
        asegurarCheck("ck_usuario_iniciales",
                "ALTER TABLE usuario ADD CONSTRAINT ck_usuario_iniciales CHECK (iniciales REGEXP '^[A-Z]{2,3}$')");
        int n = SECUENCIA.incrementAndGet();
        String documento = "2088" + String.format("%07d", n);
        Distrito distrito = ubigeo("Ica", "Ica", "Ica", n);
        cliente(distrito, documento, "3M Perú", "Quimica " + n);
        rolCliente();

        String cuerpo = """
                {"razonSocial":"3M Perú","documento":"%s","nombreContacto":"3M Perú",
                 "correo":"tresm%s@portal.pe","password":"clave1234"}
                """.formatted(documento, n);
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        Usuario creado = usuarioRepository.findByCorreoIgnoreCaseAndStatus("tresm" + n + "@portal.pe", "A").orElseThrow();
        assertEquals("MP", creado.getIniciales());
        assertTrue(INICIALES.matcher(creado.getIniciales()).matches());

        String documentoSinLetras = "2089" + String.format("%07d", n);
        cliente(distrito, documentoSinLetras, "9", "Sin letras " + n);
        String sinLetras = """
                {"razonSocial":"9","documento":"%s","nombreContacto":"1",
                 "correo":"sinletras%s@portal.pe","password":"clave1234"}
                """.formatted(documentoSinLetras, n);
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(sinLetras))
                .andExpect(status().isOk());
        Usuario fallback = usuarioRepository.findByCorreoIgnoreCaseAndStatus("sinletras" + n + "@portal.pe", "A").orElseThrow();
        assertEquals("CL", fallback.getIniciales());
    }

    @Test
    void nombreUsuarioConSufijoNoPasaDe50() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String ocupado = "a".repeat(50);
        String documento = "2066" + String.format("%07d", n);
        Distrito distrito = ubigeo("Loreto", "Maynas", "Iquitos", n);
        Cliente cliente = cliente(distrito, documento, "Largo " + n, "Largo " + n);
        Cliente otro = cliente(distrito, "2067" + String.format("%07d", n), "Otro largo " + n, "Otro largo " + n);
        Rol rol = rolCliente();
        usuarioPortal(ocupado, "ocupado.largo" + n + "@portal.pe", otro, rol);

        String cuerpo = """
                {"razonSocial":"Largo","documento":"%s","nombreContacto":"Ana Ruiz",
                 "correo":"%s@portal.pe","password":"clave1234"}
                """.formatted(documento, "a".repeat(60));
        mockMvc.perform(post("/api/portal/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        Usuario creado = usuarioRepository.findByCorreoIgnoreCaseAndStatus("a".repeat(60) + "@portal.pe", "A").orElseThrow();
        assertTrue(creado.getNombreUsuario().length() <= 50);
        assertEquals(50, creado.getNombreUsuario().length());
        assertNotEquals(ocupado, creado.getNombreUsuario());
        assertEquals(cliente.getIdCliente(), creado.getCliente().getIdCliente());
    }

    @Test
    void loginRechazaClienteInactivoAunqueNoEsteBloqueado() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito distrito = ubigeo("Tacna", "Tacna", "Tacna", n);
        Cliente cliente = cliente(distrito, "2077" + String.format("%07d", n), "Inactivo " + n, "Inactivo " + n);
        Rol rol = rolCliente();
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario("inactivo." + n);
            e.setNombres("Cliente Inactivo");
            e.setCorreo("inactivo" + n + "@portal.pe");
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("CI");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
            e.setStatus("I");
        }));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"inactivo." + n + "\",\"password\":\"clave1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.message").value(containsString("inactivo")));
    }

    @Test
    void clienteSinIdClienteRecibe403EnPedidosYSolicitudes() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Rol rol = rolCliente();
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setNombreUsuario("suelto." + n);
            e.setNombres("Sin Cliente");
            e.setCorreo("suelto" + n + "@portal.pe");
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("SC");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
            e.setStatus("A");
        }));
        String cuerpo = """
                {"asunto":"No debe crearse","descripcion":"Esta solicitud no pertenece a ningun cliente"}
                """;

        mockMvc.perform(get("/api/portal/pedidos").with(user("suelto." + n).roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/portal/solicitudes")
                        .with(user("suelto." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void pedidosQuedanAisladosPorClienteYLaSolicitudNacePendiente() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito distrito = ubigeo("Cusco", "Cusco", "Wanchaq", n);
        Cliente clienteA = cliente(distrito, "2011" + String.format("%07d", n), "Cliente A " + n, "Minas A " + n);
        Cliente clienteB = cliente(distrito, "2012" + String.format("%07d", n), "Cliente B " + n, "Minas B " + n);
        Rol rol = rolCliente();
        usuarioPortal("cliente.a." + n, "a" + n + "@portal.pe", clienteA, rol);
        usuarioPortal("cliente.b." + n, "b" + n + "@portal.pe", clienteB, rol);

        String cuerpo = """
                {"asunto":"Repuestos de faja","descripcion":"Necesito placas de desgaste para la faja principal",
                 "items":[{"descripcion":"Placa de desgaste","cantidad":2,"unidad":"UND"}]}
                """;
        mockMvc.perform(post("/api/portal/solicitudes")
                        .with(user("cliente.a." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.mensaje").exists());

        String listado = mockMvc.perform(get("/api/portal/pedidos").with(user("cliente.a." + n).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].asunto").value("Repuestos de faja"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].codigo").value(org.hamcrest.Matchers.matchesPattern("SOL-\\d{4}-\\d+")))
                .andExpect(jsonPath("$[0].items[0].descripcion").value("Placa de desgaste"))
                .andExpect(jsonPath("$[0].items[0].cantidad").value(2))
                .andExpect(jsonPath("$[0].total").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode pedidos = new ObjectMapper().readTree(listado);
        long idPedido = pedidos.get(0).get("id").asLong();

        mockMvc.perform(get("/api/portal/pedidos/" + idPedido).with(user("cliente.a." + n).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asunto").value("Repuestos de faja"));

        mockMvc.perform(get("/api/portal/pedidos").with(user("cliente.b." + n).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/portal/pedidos/" + idPedido).with(user("cliente.b." + n).roles("CLIENTE")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/portal/pedidos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/pedidos").with(user("vendedor." + n).roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/portal/solicitudes")
                        .with(user("vendedor." + n).roles("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    private void asegurarCheck(String nombre, String sql) {
        Integer existe = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE UPPER(CONSTRAINT_NAME) = ?",
                Integer.class, nombre.toUpperCase());
        if (existe != null && existe > 0) {
            return;
        }
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException ex) {
            throw new IllegalStateException("No se pudo crear " + nombre + " en H2: " + ex.getMostSpecificCause().getMessage(), ex);
        }
    }

    private Rol rolCliente() {
        return rolRepository.findByNombreRolIgnoreCase("CLIENTE")
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol("CLIENTE");
                    e.setDescripcion("Portal");
                })));
    }

    private void usuarioPortal(String nombreUsuario, String correo, Cliente cliente, Rol rol) {
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario(nombreUsuario);
            e.setNombres("Contacto " + nombreUsuario);
            e.setCorreo(correo);
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
            e.setStatus("A");
        }));
    }

    private TipoDocumento tipoRuc() {
        return tipoDocumentoRepository.findAll().stream()
                .filter(t -> "RUC".equals(t.getCodigoTipo()))
                .findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("RUC");
                    e.setNombreTipo("RUC");
                    e.setLongitudDocumento((short) 11);
                    e.setSoloNumerico(true);
                })));
    }

    private Cliente cliente(Distrito distrito, String documento, String razonSocial, String sectorNombre) {
        TipoDocumento tipo = tipoDocumentoRepository.findAll().stream()
                .filter(t -> "RUC".equals(t.getCodigoTipo()))
                .findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("RUC");
                    e.setNombreTipo("RUC");
                    e.setLongitudDocumento((short) 11);
                    e.setSoloNumerico(true);
                })));
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> {
            e.setNombreSector(sectorNombre);
        }));
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipo);
            e.setDistrito(distrito);
            e.setNumeroDocumento(documento);
            e.setRazonSocial(razonSocial);
        }));
        return clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
    }

    private Distrito ubigeo(String departamentoNombre, String provinciaNombre, String distritoNombre, int n) {
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("P%02d", n % 100));
            e.setNombrePais("Pais " + n);
        }));
        Departamento departamento = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento(departamentoNombre);
        }));
        Provincia provincia = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia(provinciaNombre);
        }));
        return distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo(String.format("%06d", n));
            e.setNombreDistrito(distritoNombre);
        }));
    }

    private <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        if (entidad.getStatus() == null) {
            entidad.setStatus("A");
        }
        return entidad;
    }
}
