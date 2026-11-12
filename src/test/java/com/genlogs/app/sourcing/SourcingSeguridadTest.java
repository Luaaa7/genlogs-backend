package com.genlogs.app.sourcing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoints de búsqueda por rol, con el interruptor ENCENDIDO solo para esta prueba.
 * Matriz v3.2 "Búsqueda e historial": Asist. Log. crea; Gerente y Jefe Adm. solo ven;
 * Contador, SST, Asist. Adm. y Cliente sin acceso.
 */
@SpringBootTest(properties = "genlogs.busqueda.habilitada=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourcingSeguridadTest {

    private static final String BUSCAR = "/api/sourcing/buscar";
    private static final String CUERPO = "{\"numeroParte\":\"1R-0750\",\"forzar\":false}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/sourcing/historial").param("numeroParte", "1R-0750"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE_GENERAL", "JEFE_ADMINISTRATIVO"})
    void gerenteYJefeVenElHistorial(String rol) throws Exception {
        mockMvc.perform(get("/api/sourcing/historial").param("numeroParte", "1R-0750")
                        .with(user("u").roles(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "CONTADOR_GENERAL", "CLIENTE"})
    void soloElAsistenteLogisticoBusca(String rol) throws Exception {
        mockMvc.perform(post(BUSCAR).with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTADOR_GENERAL", "CLIENTE"})
    void sinAccesoAlHistorial(String rol) throws Exception {
        mockMvc.perform(get("/api/sourcing/historial").param("numeroParte", "1R-0750")
                        .with(user("u").roles(rol)))
                .andExpect(status().isForbidden());
    }

    /**
     * Hoy ASISTENTE_LOGISTICO no está en ROLES_INTERNOS de SecurityConfig y el filtro
     * lo corta con 403. Lo agrega Karla en feat/be-a-security-config (Ola 1).
     * Cuando ese PR entre a release/v3.0, quita el @Disabled.
     */
    @Test
    @Disabled("Activar cuando feat/be-a-security-config agregue ASISTENTE_LOGISTICO a ROLES_INTERNOS")
    void asistenteLogisticoBuscaYSinFuentesSugierePegarUrl() throws Exception {
        mockMvc.perform(post(BUSCAR).with(user("asistente").roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reutilizada").value(false))
                .andExpect(jsonPath("$.sugerirUrl").value(true));
    }

    @Test
    @Disabled("Activar cuando feat/be-a-security-config agregue ASISTENTE_LOGISTICO a ROLES_INTERNOS")
    void numeroParteVacioResponde400() throws Exception {
        mockMvc.perform(post(BUSCAR).with(user("asistente").roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroParte\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
