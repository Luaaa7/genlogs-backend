package com.genlogs.app.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-A: anular un comprobante (PATCH /api/facturacion/{id}/estado) es del
 * JEFE_ADMINISTRATIVO, no del ADMINISTRADOR (que queda solo como transitorio).
 * Con un id inexistente, quien tiene permiso llega al servicio (404) y el resto
 * se queda en la seguridad (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnulacionFacturaPermisosTest {

    private static final String ANULAR = "{\"codigoEstado\":\"ANULADA\"}";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"JEFE_ADMINISTRATIVO", "ADMINISTRADOR"})
    void elJefeAdministrativoPuedeAnular(String rol) throws Exception {
        mockMvc.perform(patch("/api/facturacion/999999/estado")
                        .with(user("anula").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANULAR))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTADOR_GENERAL", "GERENTE_GENERAL", "VENDEDOR", "CLIENTE"})
    void elRestoNoPuedeAnular(String rol) throws Exception {
        mockMvc.perform(patch("/api/facturacion/999999/estado")
                        .with(user("otro").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANULAR))
                .andExpect(status().isForbidden());
    }
}
