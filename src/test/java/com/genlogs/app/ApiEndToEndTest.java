package com.genlogs.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Prueba end-to-end por HTTP contra el servidor real (Tomcat, cadena de
 * seguridad, JWT, controller, service y repositorio). Es la verificación que
 * demuestra que el backend "funciona": no solo que compila.
 *
 * Base de datos H2 propia para no interferir con los demás contextos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:e2edb;DB_CLOSE_DELAY=-1")
class ApiEndToEndTest {

    private static final String USUARIO = "vendedor.e2e";
    private static final String PASSWORD = "Demo1234";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaProductoRepository categoriaProductoRepository;

    @Autowired
    private UnidadMedidaRepository unidadMedidaRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        seedProductos();
        if (usuarioRepository.findByNombreUsuario(USUARIO).isPresent()) {
            return;
        }
        Rol rol = rolRepository.save(auditar(new Rol(), e -> {
            e.setNombreRol("VENDEDOR");
            e.setDescripcion("Vendedor");
        }));
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setNombreUsuario(USUARIO);
            e.setNombres("Vendedor E2E");
            e.setCorreo("vendedor.e2e@genlogs.pe");
            e.setPasswordHash(passwordEncoder.encode(PASSWORD));
            e.setIniciales("VE");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
    }

    @Test
    void health_esPublico() throws Exception {
        HttpResponse<String> res = get("/api/health", null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("UP"));
    }

    @Test
    void cotizaciones_sinToken_responde401() throws Exception {
        assertEquals(401, get("/api/cotizaciones", null).statusCode());
    }

    @Test
    void cotizaciones_conTokenInvalido_responde401_yNo500() throws Exception {
        assertEquals(401, get("/api/cotizaciones", "token-que-no-esta-firmado").statusCode());
    }

    @Test
    void login_conPasswordIncorrecta_responde4xx_yNo500() throws Exception {
        HttpResponse<String> res = post("/api/auth/login",
                "{\"nombreUsuario\":\"" + USUARIO + "\",\"password\":\"incorrecta\"}");
        assertTrue(res.statusCode() >= 400 && res.statusCode() < 500,
                "Se esperaba 4xx, llegó " + res.statusCode() + ": " + res.body());
    }

    @Test
    void login_correcto_devuelveToken() throws Exception {
        HttpResponse<String> res = post("/api/auth/login",
                "{\"nombreUsuario\":\"" + USUARIO + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"token\""), res.body());
    }

    /**
     * El caso que originó el reporte: el frontend manda sortBy=fechaCreacion
     * y el backend respondía 500. Ahora responde 200.
     */
    @Test
    void listar_conSortByLegado_devuelve200() throws Exception {
        HttpResponse<String> res = get("/api/cotizaciones?sortBy=fechaCreacion&sortDir=DESC&page=0&size=10", token());
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"content\""), res.body());
        assertTrue(res.body().contains("\"totalElements\""), res.body());
        assertTrue(res.body().contains("\"currentPage\""), res.body());
        assertTrue(res.body().contains("\"pageSize\""), res.body());
    }

    @Test
    void listar_conSortByInvalido_devuelve400_yNo500() throws Exception {
        HttpResponse<String> res = get("/api/cotizaciones?sortBy=campoInventado", token());
        assertEquals(400, res.statusCode(), res.body());
        assertTrue(res.body().contains("Campo de ordenamiento inválido"), res.body());
    }

    @Test
    void listar_conTotalEnSortBy_devuelve400_yNo500() throws Exception {
        assertEquals(400, get("/api/cotizaciones?sortBy=total", token()).statusCode());
    }

    @Test
    void listar_conSortDirInvalido_devuelve400_yNo500() throws Exception {
        assertEquals(400, get("/api/cotizaciones?sortBy=fechaCotizacion&sortDir=NO_VALIDA", token()).statusCode());
    }

    @Test
    void detalleDeCotizacionInexistente_devuelve404_yNo500() throws Exception {
        assertEquals(404, get("/api/cotizaciones/999999", token()).statusCode());
    }

    // --- Catálogo de repuestos: orden global por código y paginación ---

    @Test
    void catalogo_devuelve12ProductosPorPaginaOrdenadosPorCodigo() throws Exception {
        HttpResponse<String> res = get("/api/productos?page=0&size=12", token());

        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"content\""), res.body());
        assertTrue(res.body().contains("\"totalElements\":15"), res.body());
        assertTrue(res.body().contains("\"totalPages\":2"), res.body());
        assertTrue(res.body().contains("\"page\":0"), res.body());
        assertTrue(res.body().contains("\"size\":12"), res.body());

        List<String> codigos = codigosDe(res.body());
        assertEquals(12, codigos.size());
        assertEquals("GLB-000001", codigos.get(0));
        assertEquals("GLB-000012", codigos.get(11));
    }

    @Test
    void catalogo_segundaPaginaContinuaLaSecuencia() throws Exception {
        HttpResponse<String> res = get("/api/productos?page=1&size=12", token());

        assertEquals(200, res.statusCode(), res.body());
        List<String> codigos = codigosDe(res.body());
        assertEquals(3, codigos.size());
        assertEquals("GLB-000013", codigos.get(0));
        assertEquals("GLB-000015", codigos.get(2));
    }

    @Test
    void catalogo_buscaPorCodigoYPorNombre() throws Exception {
        HttpResponse<String> porCodigo = get("/api/productos?codigo=000013", token());
        assertEquals(200, porCodigo.statusCode(), porCodigo.body());
        assertTrue(porCodigo.body().contains("\"totalElements\":1"), porCodigo.body());
        assertTrue(porCodigo.body().contains("GLB-000013"), porCodigo.body());

        HttpResponse<String> porNombre = get("/api/productos?nombre=Repuesto%2002", token());
        assertEquals(200, porNombre.statusCode(), porNombre.body());
        assertTrue(porNombre.body().contains("\"totalElements\":1"), porNombre.body());
    }

    @Test
    void catalogo_tamanioDePaginaSuperadoSeAcotaA100() throws Exception {
        HttpResponse<String> res = get("/api/productos?size=500", token());

        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"size\":100"), res.body());
    }

    /** Extrae los codigoProducto de la respuesta JSON, en el orden en que llegan. */
    private List<String> codigosDe(String json) {
        List<String> codigos = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"codigoProducto\":\"([^\"]+)\"").matcher(json);
        while (m.find()) {
            codigos.add(m.group(1));
        }
        return codigos;
    }

    // ------------------------------------------------------------------

    /** 15 repuestos GLB-000001..GLB-000015, insertados en orden inverso y con nombres inversos. */
    private void seedProductos() {
        if (productoRepository.findByCodigoProducto("GLB-000001").isPresent()) {
            return;
        }
        CategoriaProducto categoria = categoriaProductoRepository.save(auditar(new CategoriaProducto(), e -> {
            e.setNombreCategoria("General E2E");
            e.setSlugWeb("general-e2e");
            e.setNivel((short) 1);
        }));
        UnidadMedida unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("UNDE2E");
            e.setNombreUnidad("Unidad E2E");
        }));
        for (int i = 15; i >= 1; i--) {
            final int numero = i;
            productoRepository.save(auditar(new Producto(), e -> {
                e.setCodigoProducto(String.format("GLB-%06d", numero));
                e.setNombreProducto(String.format("Repuesto %02d", 16 - numero));
                e.setCategoriaProducto(categoria);
                e.setUnidadMedida(unidad);
                e.setVisibleWeb(true);
            }));
        }
    }

    private String token() throws Exception {
        HttpResponse<String> res = post("/api/auth/login",
                "{\"nombreUsuario\":\"" + USUARIO + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(200, res.statusCode(), res.body());
        String body = res.body();
        int inicio = body.indexOf("\"token\":\"");
        assertTrue(inicio > 0, body);
        int fin = body.indexOf('"', inicio + 9);
        return body.substring(inicio + 9, fin);
    }

    private HttpResponse<String> get(String ruta, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String ruta, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private <T extends com.genlogs.app.model.Auditable> T auditar(T entidad,
            java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        return entidad;
    }
}
