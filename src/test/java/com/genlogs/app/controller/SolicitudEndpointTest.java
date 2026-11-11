package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.CloudinaryService;

/**
 * Endpoint único de solicitudes (BE-B). POST /api/solicitudes y POST /api/solicitudes/fotos.
 * D-01 por canal: PORTAL sin N.º de parte exige foto o plano; LANDING acepta solo descripción;
 * VITRINA exige idProducto. Con N.º de parte siempre se pide la marca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SolicitudEndpointTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(700);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
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
    @Autowired private CategoriaProductoRepository categoriaProductoRepository;
    @Autowired private UnidadMedidaRepository unidadMedidaRepository;

    @MockitoBean private CloudinaryService cloudinaryService;

    // ---------------------------------------------------------------- creación

    @Test
    void portalConMarcaYNumeroDeParte_creaLaSolicitudConPlazoComunYEvento() throws Exception {
        String cliente = nuevoCliente();

        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canal":"PORTAL","asunto":"Filtros","descripcion":"Mantenimiento de 500 h",
                                 "items":[{"descripcion":"Filtro de aceite","cantidad":2,"unidad":"UND",
                                           "marcaTexto":"Caterpillar","numeroParte":"1R-0750","gtin":"7750182002011"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoSolicitud").value(org.hamcrest.Matchers.startsWith("SOL-")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();

        long id = idDe(cuerpo);
        Map<String, Object> s = jdbc.queryForMap(
                "SELECT tipo_plazo, fecha_limite_interna, estado_solicitud FROM solicitud_cotizacion WHERE id_solicitud = ?", id);
        assertThat(s.get("TIPO_PLAZO")).isEqualTo("COMUN");
        assertThat(s.get("FECHA_LIMITE_INTERNA")).isNotNull();

        Map<String, Object> item = jdbc.queryForMap(
                "SELECT marca_texto, numero_parte, gtin, url_foto FROM solicitud_item WHERE id_solicitud = ?", id);
        assertThat(item.get("MARCA_TEXTO")).isEqualTo("Caterpillar");
        assertThat(item.get("NUMERO_PARTE")).isEqualTo("1R-0750");
        assertThat(item.get("GTIN")).isEqualTo("7750182002011");
        assertThat(item.get("URL_FOTO")).isNull();

        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT tipo_evento, detalle FROM solicitud_evento WHERE id_solicitud = ?", id);
        assertThat(evento.get("TIPO_EVENTO")).isEqualTo("CREADA");
        assertThat((String) evento.get("DETALLE")).contains("PORTAL");
    }

    @Test
    void portalSinNumeroDeParteNiFoto_responde400() throws Exception {
        String cliente = nuevoCliente();
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("PORTAL", "\"descripcion\":\"Pieza rota\",\"cantidad\":1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("foto o plano")));
    }

    @Test
    void portalSinNumeroDeParteConFoto_quedaComoPlazoEspecial() throws Exception {
        String cliente = nuevoCliente();
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("PORTAL",
                                "\"descripcion\":\"Pieza rota\",\"cantidad\":1,\"urlFoto\":\"https://res.cloudinary.com/x/p.jpg\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbc.queryForObject("SELECT tipo_plazo FROM solicitud_cotizacion WHERE id_solicitud = ?",
                String.class, idDe(cuerpo))).isEqualTo("ESPECIAL");
    }

    @Test
    void landingSinNumeroDeParteNiFoto_seAceptaPorqueElAsistentePideLaFotoDespues() throws Exception {
        String cliente = nuevoCliente();
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("LANDING", "\"descripcion\":\"Pieza rota del chancador\",\"cantidad\":1")))
                .andExpect(status().isCreated());
    }

    @Test
    void numeroDeParteSinMarca_responde400() throws Exception {
        String cliente = nuevoCliente();
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("LANDING", "\"descripcion\":\"Filtro\",\"cantidad\":1,\"numeroParte\":\"1R-0750\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("marca")));
    }

    @Test
    void fotoQueNoEsHttps_responde400() throws Exception {
        String cliente = nuevoCliente();
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("PORTAL",
                                "\"descripcion\":\"Pieza\",\"cantidad\":1,\"urlFoto\":\"javascript:alert(1)\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vitrinaSinProducto_responde400() throws Exception {
        String cliente = nuevoCliente();
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("VITRINA", "\"descripcion\":\"Rodamiento\",\"cantidad\":1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vitrinaConProductoExistente_guardaElProducto() throws Exception {
        String cliente = nuevoCliente();
        long idProducto = nuevoProducto();
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("VITRINA", "\"descripcion\":\"Rodamiento\",\"cantidad\":1,\"idProducto\":" + idProducto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbc.queryForObject("SELECT id_producto FROM solicitud_item WHERE id_solicitud = ?",
                Long.class, idDe(cuerpo))).isEqualTo(idProducto);
    }

    @Test
    void vitrinaConProductoQueNoExiste_responde400() throws Exception {
        String cliente = nuevoCliente();
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("VITRINA", "\"descripcion\":\"Rodamiento\",\"cantidad\":1,\"idProducto\":999999")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinDescripcionGeneral_usaElAsunto() throws Exception {
        String cliente = nuevoCliente();
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canal":"LANDING","asunto":"Caterpillar 1R-0750",
                                 "items":[{"descripcion":"Filtro","cantidad":1,"marcaTexto":"Caterpillar","numeroParte":"1R-0750"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbc.queryForObject("SELECT descripcion FROM solicitud_cotizacion WHERE id_solicitud = ?",
                String.class, idDe(cuerpo))).isEqualTo("Caterpillar 1R-0750");
    }

    @Test
    void personalInterno_noCreaPorEsteEndpointTodavia() throws Exception {
        mockMvc.perform(post("/api/solicitudes").with(user("jefe.adm").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unItem("PORTAL", "\"descripcion\":\"Filtro\",\"cantidad\":1,\"marcaTexto\":\"CAT\",\"numeroParte\":\"1R\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void masDeDiezSolicitudesSeguidas_responde429() throws Exception {
        String cliente = nuevoCliente();
        String cuerpo = unItem("LANDING", "\"descripcion\":\"Filtro\",\"cantidad\":1");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------- fotos

    @Test
    void clienteSubeFotoYRecibeLaUrl() throws Exception {
        when(cloudinaryService.subirImagen(any(), anyString())).thenReturn("https://res.cloudinary.com/x/pieza.jpg");
        MockMultipartFile foto = new MockMultipartFile("file", "pieza.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/solicitudes/fotos").file(foto).with(user("cliente.foto").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://res.cloudinary.com/x/pieza.jpg"));
    }

    @Test
    void planoEnPdf_seSubeComoDocumento() throws Exception {
        when(cloudinaryService.subirDocumento(any(), anyString())).thenReturn("https://res.cloudinary.com/x/plano.pdf");
        MockMultipartFile plano = new MockMultipartFile("file", "plano.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/solicitudes/fotos").file(plano).with(user("cliente.foto").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://res.cloudinary.com/x/plano.pdf"));
    }

    @Test
    void archivoQueNoEsFotoNiPdf_responde400() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/x-msdownload", new byte[]{1});
        mockMvc.perform(multipart("/api/solicitudes/fotos").file(exe).with(user("cliente.foto").roles("CLIENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fotoDeCelularDe6Mb_seAcepta() throws Exception {
        when(cloudinaryService.subirImagen(any(), anyString())).thenReturn("https://res.cloudinary.com/x/cel.jpg");
        MockMultipartFile foto = new MockMultipartFile("file", "cel.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);
        mockMvc.perform(multipart("/api/solicitudes/fotos").file(foto).with(user("cliente.foto").roles("CLIENTE")))
                .andExpect(status().isOk());
    }

    @Test
    void fotoDeMasDe10Mb_responde400() throws Exception {
        MockMultipartFile grande = new MockMultipartFile("file", "grande.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/solicitudes/fotos").file(grande).with(user("cliente.foto").roles("CLIENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subirFotoSinSesion_responde401() throws Exception {
        MockMultipartFile foto = new MockMultipartFile("file", "pieza.jpg", "image/jpeg", new byte[]{1});
        mockMvc.perform(multipart("/api/solicitudes/fotos").file(foto))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- datos de prueba

    private static String unItem(String canal, String camposItem) {
        return "{\"canal\":\"" + canal + "\",\"asunto\":\"Pedido de prueba\",\"descripcion\":\"Detalle del pedido\","
                + "\"items\":[{" + camposItem + "}]}";
    }

    private static long idDe(String json) {
        return Long.parseLong(json.replaceAll(".*\"idSolicitud\":(\\d+).*", "$1"));
    }

    /** Crea tercero, cliente y usuario CLIENTE activos; devuelve el nombre de usuario. */
    private String nuevoCliente() {
        int n = SECUENCIA.incrementAndGet();
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("Q%02d", n % 100));
            e.setNombrePais("Pais " + n);
        }));
        Departamento dep = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Dep " + n);
        }));
        Provincia prov = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(dep);
            e.setNombreProvincia("Prov " + n);
        }));
        Distrito dist = distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(prov);
            e.setUbigeo(String.format("%06d", n));
            e.setNombreDistrito("Dist " + n);
        }));
        TipoDocumento ruc = tipoDocumentoRepository.findAll().stream()
                .filter(t -> "RUC".equals(t.getCodigoTipo())).findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("RUC");
                    e.setNombreTipo("RUC");
                    e.setLongitudDocumento((short) 11);
                    e.setSoloNumerico(true);
                })));
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> e.setNombreSector("Sector " + n)));
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(ruc);
            e.setDistrito(dist);
            e.setNumeroDocumento("2070" + String.format("%07d", n));
            e.setRazonSocial("Minera " + n);
        }));
        Cliente cliente = clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
        Rol rol = rolRepository.findByNombreRolIgnoreCase("CLIENTE")
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol("CLIENTE");
                    e.setDescripcion("Portal");
                })));
        String nombre = "cli" + n;
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario(nombre);
            e.setNombres("Contacto " + n);
            e.setCorreo(nombre + "@minera.pe");
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        return nombre;
    }

    private long nuevoProducto() {
        int n = SECUENCIA.incrementAndGet();
        UnidadMedida unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("S" + n);
            e.setNombreUnidad("Unidad " + n);
        }));
        CategoriaProducto cat = categoriaProductoRepository.save(auditar(new CategoriaProducto(), e -> {
            e.setNombreCategoria("Rodamientos " + n);
            e.setSlugWeb("rodamientos-" + n);
            e.setNivel((short) 1);
        }));
        Producto p = productoRepository.save(auditar(new Producto(), e -> {
            e.setCategoriaProducto(cat);
            e.setUnidadMedida(unidad);
            e.setCodigoProducto("VIT-" + n);
            e.setNombreProducto("Rodamiento " + n);
            e.setVisibleWeb(true);
        }));
        return p.getIdProducto();
    }

    private <T extends Auditable> T auditar(T entidad, Consumer<T> completar) {
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
