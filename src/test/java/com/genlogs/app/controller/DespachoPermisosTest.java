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
    void contadorYClienteNoEntranAlDespachoInterno() throws Exception {
        for (String rol : new String[] {"CONTADOR_GENERAL", "CLIENTE"}) {
            mockMvc.perform(get("/api/despachos").with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/despachos/1/etapa")
                            .with(user("u").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"etapa\":\"EN_PREPARACION\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Disabled("Depende de que SecurityConfig deje pasar en /api/despachos/** a "
            + "ASISTENTE_LOGISTICO, ASISTENTE_ADMINISTRATIVO y SUPERVISOR_SST. "
            + "Hoy anyRequest().hasAnyRole(ROLES_INTERNOS) responde 403 antes de @PreAuthorize. "
            + "Con el filtro nuevo el supervisor ve (está en VER), confirma el SST y el PATCH de etapa sigue en 403.")
    @Test
    void asistentesYSupervisorPasanElFiltroCuandoSecurityConfigLoIncluya() throws Exception {
        mockMvc.perform(get("/api/despachos").with(user("u").roles("ASISTENTE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/despachos")
                        .with(user("u").roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/despachos").with(user("u").roles("SUPERVISOR_SST")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/despachos/1/etapa")
                        .with(user("u").roles("SUPERVISOR_SST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"EN_PREPARACION\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/despachos/1/sst")
                        .with(user("u").roles("SUPERVISOR_SST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PASE\",\"fechaVencimiento\":\"2026-12-01\"}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_ADMINISTRATIVO",
            "ASISTENTE_LOGISTICO", "CONTADOR_GENERAL", "CLIENTE"})
    void quienNoEsSupervisorNoConfirmaSst(String rol) throws Exception {
        mockMvc.perform(post("/api/despachos/1/sst")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PASE\",\"fechaVencimiento\":\"2026-12-01\"}"))
                .andExpect(status().isForbidden());
    }
}
