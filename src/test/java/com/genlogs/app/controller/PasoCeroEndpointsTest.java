package com.genlogs.app.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Paso 0: reglas de URL y contratos de la campanita y del endpoint unico de solicitudes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasoCeroEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SOLICITUD_VALIDA = """
            {"canal":"PORTAL","asunto":"Filtro hidraulico",
             "items":[{"marcaTexto":"Caterpillar","numeroParte":"1R-0750","descripcion":"Filtro de combustible","cantidad":2}]}
            """;

    @Test
    void campanita_sinSesion_401() throws Exception {
        mockMvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
    }

    @Test
    void campanita_cliente_403() throws Exception {
        mockMvc.perform(get("/api/notificaciones").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void campanita_personalInterno_200ConContador() throws Exception {
        mockMvc.perform(get("/api/notificaciones").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(0))
                .andExpect(jsonPath("$.avisos").isArray());
    }

    @Test
    void marcarLeida_avisoInexistente_404() throws Exception {
        mockMvc.perform(patch("/api/notificaciones/999999/leida").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
    }

    @Test
    void solicitudes_sinSesion_401() throws Exception {
        mockMvc.perform(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content(SOLICITUD_VALIDA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void solicitudes_clienteConCuerpoValido_llegaAlEndpoint() throws Exception {
        // 501 mientras BE-B no lo implemente; cuando lo haga, este caso pasa a 201.
        mockMvc.perform(post("/api/solicitudes").with(user("cliente").roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD_VALIDA))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void solicitudes_gtinInvalido_400() throws Exception {
        String cuerpo = SOLICITUD_VALIDA.replace("\"numeroParte\":\"1R-0750\"", "\"gtin\":\"12AB\"");
        mockMvc.perform(post("/api/solicitudes").with(user("cliente").roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void solicitudes_sinRepuestos_400() throws Exception {
        mockMvc.perform(post("/api/solicitudes").with(user("cliente").roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"LANDING\",\"asunto\":\"Pedido\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parametrosYTipoCambio_noSonParaElCliente() throws Exception {
        mockMvc.perform(get("/api/parametros").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tipo-cambio").with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void repuestosDelPortal_noSonParaElPersonalInterno() throws Exception {
        mockMvc.perform(get("/api/portal/repuestos").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isForbidden());
    }
}