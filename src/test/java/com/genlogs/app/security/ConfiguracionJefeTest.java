package com.genlogs.app.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.genlogs.app.dto.EscalaMargenDto;
import com.genlogs.app.model.TasaIgv;
import com.genlogs.app.service.EscalaMargenService;
import com.genlogs.app.service.TasaIgvService;

/**
 * v3.4: el IGV y la escala de márgenes los edita el Jefe Administrativo (permisos.ts del
 * frontend y decisión de roles). Antes pedían el rol ADMINISTRADOR, que desapareció con
 * v3_0_roles.sql, así que demo.jefe recibía 403 al guardar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfiguracionJefeTest {

    private static final String IGV = "{\"porcentaje\":18.00,\"vigenteDesde\":\"2026-12-01\"}";
    private static final String ESCALA = "{\"productos\":[{\"costoDesde\":0,\"costoHasta\":null,"
            + "\"margenSugerido\":20,\"margenMinimo\":15}],\"servicios\":[]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TasaIgvService tasaIgvService;

    @MockitoBean
    private EscalaMargenService escalaMargenService;

    private void respuestasDeLosServicios() {
        when(tasaIgvService.registrar(any(), any())).thenReturn(new TasaIgv());
        when(escalaMargenService.guardar(any())).thenReturn(
                new EscalaMargenDto.Escala(List.of(new EscalaMargenDto.RangoProducto(
                        BigDecimal.ZERO, null, BigDecimal.valueOf(20), BigDecimal.valueOf(15))), List.of()));
    }

    @Test
    void elJefeAdministrativoGuardaElIgvYLaEscalaDeMargenes() throws Exception {
        String rol = "JEFE_ADMINISTRATIVO";
        respuestasDeLosServicios();

        mockMvc.perform(post("/api/catalogos/igv").with(user("demo.jefe").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(IGV))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/configuracion/margenes").with(user("demo.jefe").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(ESCALA))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE_GENERAL", "CONTADOR_GENERAL", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO", "SUPERVISOR_SST"})
    void losDemasRolesNoPuedenCambiarlos(String rol) throws Exception {
        respuestasDeLosServicios();

        mockMvc.perform(post("/api/catalogos/igv").with(user("otro").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(IGV))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/configuracion/margenes").with(user("otro").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(ESCALA))
                .andExpect(status().isForbidden());
    }
}
