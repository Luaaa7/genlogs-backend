package com.genlogs.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** TEMPORAL: comprueba qué responde hoy el backend a /api/reportes/preview. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:reprodb;DB_CLOSE_DELAY=-1")
class ReproducirPreviewTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (usuarioRepository.findByNombreUsuario("repro").isPresent()) {
            return;
        }
        Rol rol = new Rol();
        rol.setNombreRol("VENDEDOR");
        rol.setDescripcion("Vendedor");
        rol.setUserCreate("test");
        rol.setProcessCreate("TEST");
        rol = rolRepository.save(rol);
        Usuario u = new Usuario();
        u.setRol(rol);
        u.setNombreUsuario("repro");
        u.setNombres("Repro");
        u.setCorreo("repro@genlogs.pe");
        u.setPasswordHash(passwordEncoder.encode("Demo1234"));
        u.setIniciales("RP");
        u.setBloqueado(false);
        u.setIntentosFallidos((short) 0);
        u.setUserCreate("test");
        u.setProcessCreate("TEST");
        usuarioRepository.save(u);
    }

    @Test
    void queRespondeHoy() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String cuerpo = "{\"tipoReporte\":\"COTIZACIONES\",\"fechaInicio\":\"2026-08-23\","
                + "\"fechaFin\":\"2026-10-02\",\"formato\":\"PDF\"}";

        // Login para obtener el token
        String login = "{\"nombreUsuario\":\"repro\",\"password\":\"Demo1234\"}";
        HttpResponse<String> resLogin = client.send(HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(login))
                .build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("### login -> " + resLogin.statusCode() + " " + resLogin.body());
        String token = "";
        int i = resLogin.body().indexOf("\"token\":\"");
        if (i > 0) {
            token = resLogin.body().substring(i + 9, resLogin.body().indexOf('"', i + 9));
        }

        for (String metodo : new String[] {"POST", "GET"}) {
            HttpRequest.Builder b = HttpRequest
                    .newBuilder(URI.create("http://localhost:" + port + "/api/reportes/preview"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token);
            b = "POST".equals(metodo) ? b.POST(HttpRequest.BodyPublishers.ofString(cuerpo)) : b.GET();
            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("### " + metodo + " /api/reportes/preview -> " + res.statusCode());
            System.out.println("### body: " + res.body());
        }

        // Y el endpoint de descarga, para ver si funciona:
        HttpResponse<String> gen = client.send(HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/api/reportes/generar"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("### POST /api/reportes/generar -> " + gen.statusCode());
        System.out.println("### body: " + (gen.body().length() > 300 ? gen.body().substring(0, 300) : gen.body()));
    }
}
