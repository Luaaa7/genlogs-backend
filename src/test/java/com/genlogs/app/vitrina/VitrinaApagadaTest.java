package com.genlogs.app.vitrina;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Caso clave: con el interruptor apagado (valor por defecto) la vitrina no existe. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VitrinaApagadaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conLaVitrinaApagadaLasRutasNuevasDan404() throws Exception {
        mockMvc.perform(get("/api/public/catalogo/repuestos")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/repuestos").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/vitrina/productos").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
    }
}
