package com.genlogs.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * El rol CLIENTE queda fuera de lo interno y los roles internos fuera del portal.
 * El catálogo público responde 200. El registro sin body es 400 (la ruta es pública).
 * Un CLIENTE sin id_cliente pasa el filtro y el portal responde 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadRolesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void sinTokenLoPublicoNoPideSesionYLoInternoResponde401() throws Exception {
        mockMvc.perform(get("/api/public/catalogo"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/portal/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/catalogos/monedas"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/clientes",
            "/api/cotizaciones",
            "/api/facturacion",
            "/api/usuarios",
            "/api/tipos-documento"
    })
    void clienteRecibe403EnCadaModuloInterno(String ruta) throws Exception {
        mockMvc.perform(get(ruta).with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "ADMINISTRADOR,/api/usuarios",
            "VENDEDOR,/api/cotizaciones",
            "JEFE_ADMINISTRATIVO,/api/ordenes-compra",
            "GERENTE_GENERAL,/api/cotizaciones",
            "CONTADOR_GENERAL,/api/facturacion"
    })
    void rolInternoConservaSuAcceso(String rol, String ruta) throws Exception {
        mockMvc.perform(get(ruta).with(user("interno").roles(rol)))
                .andExpect(status().isOk());
    }

    @Test
    void lasRestriccionesDeMetodoNoSeAbren() throws Exception {
        mockMvc.perform(get("/api/usuarios").with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/cotizaciones").with(user("contador").roles("CONTADOR_GENERAL")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMINISTRADOR",
            "VENDEDOR",
            "JEFE_ADMINISTRATIVO",
            "GERENTE_GENERAL",
            "CONTADOR_GENERAL"
    })
    void rolInternoNoEntraAlPortal(String rol) throws Exception {
        mockMvc.perform(get("/api/portal/pedidos").with(user("interno").roles(rol)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientePasaLaAutorizacionDelPortal() throws Exception {
        mockMvc.perform(get("/api/portal/pedidos").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPermiteElFrontendEnProduccionYDesarrollo() {
        assertOrigenPermitido("/api/portal/pedidos", "https://genlogs-frontend-kym5.onrender.com");
        assertOrigenPermitido("/api/public/catalogo", "http://localhost:5173");
        assertOrigenPermitido("/api/portal/registro", "http://127.0.0.1:5173");
        assertOrigenPermitido("/api/clientes", "https://genlogs-frontend-prueba.onrender.com");
    }

    private void assertOrigenPermitido(String ruta, String origen) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ruta);
        CorsConfiguration configuracion = corsConfigurationSource.getCorsConfiguration(request);
        assertNotNull(configuracion, ruta);
        assertEquals(origen, configuracion.checkOrigin(origen));
    }
}
