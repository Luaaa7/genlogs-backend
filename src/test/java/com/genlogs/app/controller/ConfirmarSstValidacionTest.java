package com.genlogs.app.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.genlogs.app.exception.GlobalExceptionHandler;
import com.genlogs.app.service.DespachoService;
import com.genlogs.app.service.DespachoSstService;

/** El tipo y la fecha obligatoria se rechazan al leer el JSON, antes del servicio. */
class ConfirmarSstValidacionTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DespachoController(mock(DespachoService.class), mock(DespachoSstService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void tipoDistintoDePaseOSctrResponde400() throws Exception {
        mockMvc.perform(post("/api/despachos/1/sst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"EUROPA\",\"fechaVencimiento\":\"2026-12-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PASE")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SCTR")));
    }

    @Test
    void sinFechaDeVencimientoResponde400() throws Exception {
        mockMvc.perform(post("/api/despachos/1/sst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PASE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("vencimiento")));
    }
}
