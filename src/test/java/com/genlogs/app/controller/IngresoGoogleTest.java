package com.genlogs.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.google.CuentaGoogle;
import com.genlogs.app.security.google.VerificadorTokenGoogle;
import com.genlogs.app.service.FactilizaService;
import com.genlogs.app.service.NotificacionService;

/**
 * Ingreso del cliente con Google desde la landing y el portal, y los datos de la
 * empresa que se piden antes de su primera cotización.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngresoGoogleTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(700);
    private static final String GOOGLE = "/api/portal/google";

    @Autowired private MockMvc mockMvc;
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

    /** La firma del token de Google se prueba aparte (VerificadorTokenGoogleJwksTest). */
    @MockitoBean private VerificadorTokenGoogle verificador;
    @MockitoBean private FactilizaService factilizaService;
    @MockitoBean private NotificacionService notificacionService;

    private static String cuerpo(String credential) {
        return "{\"credential\":\"" + credential + "\"}";
    }

    @Test
    void cuentaNuevaEntraSinEmpresaYLaSegundaVezEsElMismoUsuario() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        rolCliente();
        when(verificador.verificar("tok-" + n)).thenReturn(new CuentaGoogle("sub-" + n, "nuevo" + n + "@gmail.com", "Nuevo Cliente"));

        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("tok-" + n)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.pendiente").value(false))
                .andExpect(jsonPath("$.nombre").value("Nuevo Cliente"))
                .andExpect(jsonPath("$.nombreRol").value("CLIENTE"));

        Usuario creado = usuarioRepository.findWithRolByGoogleSub("sub-" + n).orElseThrow();
        assertEquals("CLIENTE", creado.getRol().getNombreRol());
        assertEquals("nuevo" + n + "@gmail.com", creado.getCorreo());
        assertNull(creado.getCliente());
        assertEquals("A", creado.getStatus().trim());
        assertEquals("REGISTRO_GOOGLE", creado.getProcessCreate());
        assertTrue(creado.getIniciales().matches("^[A-Z]{2,3}$"));
        // La clave guardada no sirve para entrar con contraseña: es aleatoria.
        assertNotNull(creado.getPasswordHash());

        long antes = usuarioRepository.count();
        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("tok-" + n)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value(creado.getNombreUsuario()));
        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void clienteQueYaTeniaCuentaConEseCorreoSeVinculaYEntraConSuEmpresa() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Cliente cliente = cliente(ubigeo(n), "2066" + String.format("%07d", n), "Empresa " + n);
        usuario("titular." + n, "titular" + n + "@empresa.pe", cliente, rolCliente(), "A");
        when(verificador.verificar("tok-" + n)).thenReturn(new CuentaGoogle("sub-" + n, "Titular" + n + "@Empresa.pe", "Titular"));

        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("tok-" + n)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.nombreUsuario").value("titular." + n))
                .andExpect(jsonPath("$.perfilCompleto").value(true));

        Usuario vinculado = usuarioRepository.findByNombreUsuario("titular." + n).orElseThrow();
        assertEquals("sub-" + n, vinculado.getGoogleSub());
        assertEquals(cliente.getIdCliente(), vinculado.getCliente().getIdCliente());
    }

    @Test
    void elPersonalInternoNoEntraPorGoogle() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        usuario("jefe." + n, "jefe" + n + "@genlogs.pe", null, rol("JEFE_ADMINISTRATIVO"), "A");
        when(verificador.verificar("tok-" + n)).thenReturn(new CuentaGoogle("sub-" + n, "jefe" + n + "@genlogs.pe", "Jefe"));

        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("tok-" + n)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("personal")))
                .andExpect(jsonPath("$.token").doesNotExist());
        assertNull(usuarioRepository.findByNombreUsuario("jefe." + n).orElseThrow().getGoogleSub());
    }

    @Test
    void accesoPendienteNoRecibeToken() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Cliente cliente = cliente(ubigeo(n), "2067" + String.format("%07d", n), "Empresa " + n);
        Usuario pendiente = usuario("espera." + n, "espera" + n + "@empresa.pe", cliente, rolCliente(), "I");
        pendiente.setProcessCreate("REGISTRO_PORTAL_PENDIENTE");
        usuarioRepository.save(pendiente);
        when(verificador.verificar("tok-" + n)).thenReturn(new CuentaGoogle("sub-" + n, "espera" + n + "@empresa.pe", "Espera"));

        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("tok-" + n)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(true))
                .andExpect(jsonPath("$.mensaje").value(containsString("pendiente")))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void usuarioInactivoOBloqueadoNoEntra() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Cliente cliente = cliente(ubigeo(n), "2068" + String.format("%07d", n), "Empresa " + n);
        usuario("baja." + n, "baja" + n + "@empresa.pe", cliente, rolCliente(), "I");
        Usuario bloqueado = usuario("bloq." + n, "bloq" + n + "@empresa.pe", null, rolCliente(), "A");
        bloqueado.setBloqueado(true);
        usuarioRepository.save(bloqueado);
        when(verificador.verificar("baja-" + n)).thenReturn(new CuentaGoogle("s1-" + n, "baja" + n + "@empresa.pe", "Baja"));
        when(verificador.verificar("bloq-" + n)).thenReturn(new CuentaGoogle("s2-" + n, "bloq" + n + "@empresa.pe", "Bloq"));

        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("baja-" + n)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("inactivo")));
        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("bloq-" + n)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("bloqueado")));
    }

    @Test
    void tokenInvalidoResponde400SinCrearNada() throws Exception {
        rolCliente();
        when(verificador.verificar("malo")).thenThrow(new BusinessException("No pudimos validar tu cuenta de Google. Inténtalo de nuevo."));
        long antes = usuarioRepository.count();

        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo("malo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Google")));
        mockMvc.perform(post(GOOGLE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void cuentaIndicaSiFaltanLosDatosDeLaEmpresa() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        usuario("g." + n, "g" + n + "@gmail.com", null, rolCliente(), "A");

        mockMvc.perform(get("/api/portal/cuenta").with(user("g." + n).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.correo").value("g" + n + "@gmail.com"));
    }

    @Test
    void completarEmpresaNuevaValidaElRucYDejaPedirCotizaciones() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String ruc = "2072" + String.format("%07d", n);
        Distrito distrito = ubigeo(n);
        tipoRuc();
        Usuario google = usuario("g." + n, "g" + n + "@gmail.com", null, rolCliente(), "A");
        when(factilizaService.consultarPorNumero(ruc)).thenReturn(ConsultaDocumentoResponse.builder()
                .numeroDocumento(ruc).tipoDocumento("RUC").razonSocial("MINERA GOOGLE S.A.C.")
                .direccion("Av. Sol 1").idDistrito(distrito.getIdDistrito()).origen("FACTILIZA").simulado(false).build());

        // Sin empresa no puede pedir cotización.
        mockMvc.perform(post("/api/portal/solicitudes").with(user("g." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Filtros\",\"descripcion\":\"Necesito filtros de aceite\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/portal/empresa").with(user("g." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"%s","razonSocial":"Minera Google","nombreContacto":"Gina Paz","telefono":"999111222"}
                                """.formatted(ruc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(false));

        Cliente creado = clienteRepository.findByNumeroDocumento(ruc).orElseThrow();
        assertEquals("MINERA GOOGLE S.A.C.", creado.getTercero().getRazonSocial());
        assertEquals("999111222", creado.getTercero().getTelefono());
        Usuario actualizado = usuarioRepository.findById(google.getIdUsuario()).orElseThrow();
        assertEquals(creado.getIdCliente(), actualizado.getCliente().getIdCliente());
        assertEquals("Gina Paz", actualizado.getNombres());
        assertEquals("A", actualizado.getStatus().trim());

        mockMvc.perform(post("/api/portal/solicitudes").with(user("g." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Filtros\",\"descripcion\":\"Necesito filtros de aceite\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/portal/cuenta").with(user("g." + n).roles("CLIENTE")))
                .andExpect(jsonPath("$.perfilCompleto").value(true));
    }

    @Test
    void completarConEmpresaQueYaEsClienteQuedaPendienteYAvisaAlJefe() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String ruc = "2073" + String.format("%07d", n);
        Cliente cliente = cliente(ubigeo(n), ruc, "Empresa Existente " + n);
        Usuario google = usuario("g." + n, "g" + n + "@gmail.com", null, rolCliente(), "A");

        mockMvc.perform(post("/api/portal/empresa").with(user("g." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"%s","razonSocial":"Otra","nombreContacto":"Gina Paz"}
                                """.formatted(ruc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(true))
                .andExpect(jsonPath("$.mensaje").value(containsString("pendiente")));

        Usuario actualizado = usuarioRepository.findById(google.getIdUsuario()).orElseThrow();
        assertEquals(cliente.getIdCliente(), actualizado.getCliente().getIdCliente());
        assertEquals("I", actualizado.getStatus().trim());
        verify(factilizaService, never()).consultarPorNumero(anyString());
        verify(notificacionService).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.ACCESO_PORTAL_PENDIENTE),
                anyString(), contains("g" + n + "@gmail.com"), eq("/usuarios"), eq(true));

        // El Jefe Administrativo lo ve entre los pendientes y lo activa.
        mockMvc.perform(get("/api/usuarios/pendientes-portal").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUsuario == " + google.getIdUsuario() + ")]").isNotEmpty());
        mockMvc.perform(patch("/api/usuarios/" + google.getIdUsuario() + "/activar").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        assertEquals("A", usuarioRepository.findById(google.getIdUsuario()).orElseThrow().getStatus().trim());
    }

    @Test
    void completarEmpresaRechazaRucInvalidoYCuentaQueYaTieneEmpresa() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        usuario("g." + n, "g" + n + "@gmail.com", null, rolCliente(), "A");
        Cliente cliente = cliente(ubigeo(n), "2074" + String.format("%07d", n), "Empresa " + n);
        usuario("con." + n, "con" + n + "@empresa.pe", cliente, rolCliente(), "A");

        mockMvc.perform(post("/api/portal/empresa").with(user("g." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruc\":\"12345678\",\"razonSocial\":\"X\",\"nombreContacto\":\"Gina\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("RUC")));
        mockMvc.perform(post("/api/portal/empresa").with(user("con." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruc\":\"20100055237\",\"razonSocial\":\"X\",\"nombreContacto\":\"Gina\"}"))
                .andExpect(status().isConflict());
        // Personal interno no usa este paso.
        mockMvc.perform(post("/api/portal/empresa").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruc\":\"20100055237\",\"razonSocial\":\"X\",\"nombreContacto\":\"Gina\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- datos de prueba ----------

    private Rol rolCliente() {
        return rol("CLIENTE");
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombreRolIgnoreCase(nombre)
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol(nombre);
                    e.setDescripcion(nombre);
                })));
    }

    private Usuario usuario(String nombreUsuario, String correo, Cliente cliente, Rol rol, String status) {
        return usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario(nombreUsuario);
            e.setNombres("Contacto " + nombreUsuario);
            e.setCorreo(correo);
            e.setPasswordHash(passwordEncoder.encode("Clave1234"));
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
            e.setStatus(status);
        }));
    }

    @Test
    void completarEmpresaConDniCreaPersonaNaturalSinConsultarFactiliza() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String dni = String.format("%08d", 70000000 + n);
        tipoDni();
        Usuario google = usuario("d." + n, "d" + n + "@gmail.com", null, rolCliente(), "A");

        mockMvc.perform(post("/api/portal/empresa").with(user("d." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDocumento":"DNI","ruc":"%s","nombreContacto":"Rosa Quispe Mamani","telefono":"999111222"}
                                """.formatted(dni)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(false));

        Cliente creado = clienteRepository.findByNumeroDocumento(dni).orElseThrow();
        assertEquals("DNI", creado.getTercero().getTipoDocumento().getCodigoTipo());
        assertEquals("Rosa Quispe Mamani", creado.getTercero().getRazonSocial());
        assertNull(creado.getTercero().getDistrito());
        assertEquals("ACTIVO", creado.getSituacion());
        Usuario actualizado = usuarioRepository.findById(google.getIdUsuario()).orElseThrow();
        assertEquals(creado.getIdCliente(), actualizado.getCliente().getIdCliente());
        assertEquals("A", actualizado.getStatus().trim());
        verify(factilizaService, never()).consultarPorNumero(anyString());

        mockMvc.perform(get("/api/portal/cuenta").with(user("d." + n).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.empresa").value("Rosa Quispe Mamani"));
        mockMvc.perform(post("/api/portal/solicitudes").with(user("d." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Filtros\",\"descripcion\":\"Necesito filtros de aceite\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/portal/pedidos").with(user("d." + n).roles("CLIENTE")))
                .andExpect(status().isOk());
    }

    @Test
    void completarEmpresaConDniInvalidoOSinTipoSeRechaza() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        tipoDni();
        usuario("x." + n, "x" + n + "@gmail.com", null, rolCliente(), "A");

        // DNI con 7 dígitos.
        mockMvc.perform(post("/api/portal/empresa").with(user("x." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDocumento\":\"DNI\",\"ruc\":\"1234567\",\"nombreContacto\":\"Rosa Quispe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("DNI")));
        // Un RUC no vale como DNI.
        mockMvc.perform(post("/api/portal/empresa").with(user("x." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDocumento\":\"DNI\",\"ruc\":\"20123456789\",\"nombreContacto\":\"Rosa Quispe\"}"))
                .andExpect(status().isBadRequest());
        // Un DNI sin tipo se trata como RUC y se rechaza.
        mockMvc.perform(post("/api/portal/empresa").with(user("x." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruc\":\"12345678\",\"razonSocial\":\"Rosa\",\"nombreContacto\":\"Rosa Quispe\"}"))
                .andExpect(status().isBadRequest());
        // Tipo desconocido.
        mockMvc.perform(post("/api/portal/empresa").with(user("x." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDocumento\":\"CE\",\"ruc\":\"12345678\",\"nombreContacto\":\"Rosa Quispe\"}"))
                .andExpect(status().isBadRequest());
        // RUC sin razon social sigue siendo obligatoria.
        mockMvc.perform(post("/api/portal/empresa").with(user("x." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruc\":\"20123456789\",\"nombreContacto\":\"Rosa Quispe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("razón social")));
        verify(factilizaService, never()).consultarPorNumero(anyString());
        assertNull(usuarioRepository.findByNombreUsuario("x." + n).orElseThrow().getCliente());
    }

    @Test
    void completarEmpresaConDniQueYaEsClienteQuedaPendiente() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String dni = String.format("%08d", 71000000 + n);
        TipoDocumento tipo = tipoDni();
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipo);
            e.setNumeroDocumento(dni);
            e.setRazonSocial("Persona Existente");
        }));
        clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sectorEconomicoRepository.save(auditar(new SectorEconomico(), s -> s.setNombreSector("Sector " + n))));
            e.setSituacion("ACTIVO");
        }));
        Usuario nuevo = usuario("p." + n, "p" + n + "@gmail.com", null, rolCliente(), "A");

        mockMvc.perform(post("/api/portal/empresa").with(user("p." + n).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDocumento\":\"DNI\",\"ruc\":\"" + dni + "\",\"nombreContacto\":\"Otra Persona\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(true));

        Usuario actualizado = usuarioRepository.findById(nuevo.getIdUsuario()).orElseThrow();
        assertEquals("I", actualizado.getStatus().trim());
        verify(notificacionService).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.ACCESO_PORTAL_PENDIENTE),
                anyString(), contains("DNI " + dni), eq("/usuarios"), eq(true));
        verify(factilizaService, never()).consultarPorNumero(anyString());
    }

    private TipoDocumento tipoDni() {
        return tipoDocumentoRepository.findAll().stream()
                .filter(t -> "DNI".equals(t.getCodigoTipo()))
                .findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("DNI");
                    e.setNombreTipo("DNI");
                    e.setLongitudDocumento((short) 8);
                    e.setSoloNumerico(true);
                })));
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

    private Cliente cliente(Distrito distrito, String documento, String razonSocial) {
        TipoDocumento tipo = tipoRuc();
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> e.setNombreSector("Sector " + documento)));
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

    private Distrito ubigeo(int n) {
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("G%02d", n % 100));
            e.setNombrePais("Pais G" + n);
        }));
        Departamento departamento = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Lima");
        }));
        Provincia provincia = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia("Lima");
        }));
        return distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo(String.format("9%05d", n));
            e.setNombreDistrito("Surco");
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
