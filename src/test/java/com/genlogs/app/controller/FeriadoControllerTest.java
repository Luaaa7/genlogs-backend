package com.genlogs.app.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Permisos de /api/feriados (D-18): todo el personal interno ve; solo el Jefe Administrativo edita. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeriadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final String fechaFutura = LocalDate.now().plusMonths(2).toString();

    private String cuerpo(String fecha) {
        return "{\"fecha\":\"" + fecha + "\",\"descripcion\":\"Día no laborable\"}";
    }

    @Test
    void personalInternoPuedeListar() throws Exception {
        mockMvc.perform(get("/api/feriados").with(user("gerente").roles("GERENTE_GENERAL")))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeVerFeriados() throws Exception {
        mockMvc.perform(get("/api/feriados").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jefeAdministrativoCreaYDaDeBaja() throws Exception {
        String respuesta = mockMvc.perform(post("/api/feriados")
                        .with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(fechaFutura)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fecha").value(fechaFutura))
                .andExpect(jsonPath("$.status").value("A"))
                .andReturn().getResponse().getContentAsString();

        String id = respuesta.replaceAll(".*\"idFeriado\":(\\d+).*", "$1");

        mockMvc.perform(patch("/api/feriados/" + id + "/baja").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNoContent());
    }

    @Test
    void gerenteNoPuedeCrear() throws Exception {
        mockMvc.perform(post("/api/feriados")
                        .with(user("gerente").roles("GERENTE_GENERAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(fechaFutura)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fechaPasadaResponde400() throws Exception {
        mockMvc.perform(post("/api/feriados")
                        .with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(LocalDate.now().minusDays(1).toString())))
                .andExpect(status().isBadRequest());
    }
}
