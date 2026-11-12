package com.genlogs.app.vitrina;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Vitrina ENCENDIDA solo para esta prueba: quién ve qué (RF-82, D-13, matriz v3.2). */
@SpringBootTest(properties = "genlogs.vitrina.habilitada=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VitrinaSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void laLandingVeElCatalogoSinSesion() throws Exception {
        mockMvc.perform(get("/api/public/catalogo/repuestos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/public/catalogo/categorias"))
                .andExpect(status().isOk());
    }

    @Test
    void repuestosDelPortalSinSesionResponde401() throws Exception {
        mockMvc.perform(get("/api/portal/repuestos")).andExpect(status().isUnauthorized());
    }

    @Test
    void elClienteVeLosRepuestosDelPortal() throws Exception {
        mockMvc.perform(get("/api/portal/repuestos").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"JEFE_ADMINISTRATIVO", "GERENTE_GENERAL", "CONTADOR_GENERAL"})
    void elPersonalNoUsaLosRepuestosDelPortal(String rol) throws Exception {
        mockMvc.perform(get("/api/portal/repuestos").with(user("u").roles(rol)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elClienteNoEntraALaVitrinaInterna() throws Exception {
        mockMvc.perform(get("/api/vitrina/productos").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elJefeVeLaVitrinaInternaPeroNoConvierte() throws Exception {
        mockMvc.perform(get("/api/vitrina/productos").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/vitrina/productos").with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idSourcing\":1,\"nombre\":\"Filtro\",\"idCategoriaProducto\":1,\"idUnidadMedida\":1,\"tipoRepuesto\":\"OEM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unRepuestoQueNoExisteResponde404() throws Exception {
        mockMvc.perform(get("/api/public/catalogo/repuestos/999999")).andExpect(status().isNotFound());
    }
}
