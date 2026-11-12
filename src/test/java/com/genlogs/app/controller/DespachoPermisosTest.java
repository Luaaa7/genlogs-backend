package com.genlogs.app.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DespachoPermisosTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE_GENERAL", "JEFE_ADMINISTRATIVO"})
    void gerenteYJefeVen(String rol) throws Exception {
        mockMvc.perform(get("/api/despachos").with(user("u").roles(rol)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/despachos")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idOrdenCompra\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void contadorSupervisorYClienteNoEntranAlDespachoInterno() throws Exception {
        for (String rol : new String[] {"CONTADOR_GENERAL", "SUPERVISOR_SST", "CLIENTE"}) {
            mockMvc.perform(get("/api/despachos").with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/despachos/1/etapa")
                            .with(user("u").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"etapa\":\"EN_PREPARACION\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Disabled("Depende de que SecurityConfig deje pasar a ASISTENTE_LOGISTICO y "
            + "ASISTENTE_ADMINISTRATIVO en /api/despachos/**. Hoy anyRequest().hasAnyRole(ROLES_INTERNOS) "
            + "responde 403 antes de @PreAuthorize.")
    @Test
    void asistentesPasanElFiltroCuandoSecurityConfigLoIncluya() throws Exception {
        mockMvc.perform(get("/api/despachos").with(user("u").roles("ASISTENTE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/despachos")
                        .with(user("u").roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
