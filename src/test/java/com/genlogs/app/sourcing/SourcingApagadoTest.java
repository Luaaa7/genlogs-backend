package com.genlogs.app.sourcing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Caso clave: con el interruptor apagado (valor por defecto) los endpoints responden 404. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourcingApagadoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conElInterruptorApagadoLaBusquedaNoExiste() throws Exception {
        mockMvc.perform(post("/api/sourcing/buscar").with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroParte\":\"1R-0750\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/sourcing/historial").param("numeroParte", "1R-0750")
                        .with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
    }
}
