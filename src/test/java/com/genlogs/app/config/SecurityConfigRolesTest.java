package com.genlogs.app.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Filtro de URL de SecurityConfig (BE-A): los 6 roles internos de la v3.2 pasan,
 * CLIENTE no, y /api/empresas-mineras ya no es público.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigRolesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void empresasMinerasSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/empresas-mineras"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void losCatalogosSiguenPublicosParaConsultar() throws Exception {
        mockMvc.perform(get("/api/catalogos/monedas"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "CONTADOR_GENERAL",
            "SUPERVISOR_SST", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"})
    void losSeisRolesInternosPasanElFiltro(String rol) throws Exception {
        // La campanita no tiene @PreAuthorize: solo la corta el filtro de URL.
        mockMvc.perform(get("/api/notificaciones").with(user("u").roles(rol)))
                .andExpect(status().isOk());
    }

    @Test
    void elClienteNoEntraALoInterno() throws Exception {
        mockMvc.perform(get("/api/notificaciones").with(user("u").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }
}
