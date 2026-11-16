package com.genlogs.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.service.CotizacionService;
import com.genlogs.app.service.RespuestaClienteService;

@SpringJUnitConfig(CotizacionActualizarPermisosTest.SeguridadMinima.class)
@WebAppConfiguration
class CotizacionActualizarPermisosTest {

    private static final String CUERPO = """
            {"idCliente":1,"idMoneda":1,"idCondicionPago":1,
             "lineas":[{"descripcion":"Filtro","cantidad":1,"precioUnitario":10}]}
            """;

    @Autowired private WebApplicationContext contexto;
    @Autowired private CotizacionService servicio;
    @Autowired private RespuestaClienteService respuestas;

    private MockMvc mockMvc;

    @BeforeEach
    void mvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        reset(servicio);
        when(servicio.actualizar(anyLong(), any())).thenReturn(CotizacionResponse.builder().idCotizacion(5L).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASISTENTE_LOGISTICO", "JEFE_ADMINISTRATIVO"})
    void quienCreaPuedeActualizar(String rol) throws Exception {
        mockMvc.perform(put("/api/cotizaciones/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO)
                        .with(user("ana").roles(rol)))
                .andExpect(status().isOk());

        verify(servicio).actualizar(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ASISTENTE_ADMINISTRATIVO", "CONTADOR_GENERAL", "SUPERVISOR_SST", "GERENTE_GENERAL"
    })
    void quienNoCreaRecibe403(String rol) throws Exception {
        mockMvc.perform(put("/api/cotizaciones/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO)
                        .with(user("otro").roles(rol)))
                .andExpect(status().isForbidden());

        verify(servicio, never()).actualizar(anyLong(), any());
    }

    @org.junit.jupiter.api.Test
    void soloElJefeRegistraLaRespuestaDelCliente() throws Exception {
        when(respuestas.registrar(anyLong(), any())).thenReturn("APROBADA");
        when(servicio.buscarPorId(anyLong())).thenReturn(CotizacionResponse.builder().idCotizacion(8L).build());
        String cuerpo = """
                {"codigoEstado":"APROBADA","comentario":"Llamó el 16/11 a las 9:00","idAdjunto":7}
                """;

        mockMvc.perform(post("/api/cotizaciones/8/respuesta-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo)
                        .with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        verify(respuestas).registrar(anyLong(), any());
        verify(servicio).registrarOrdenSiAprobada(8L, "APROBADA");

        for (String rol : new String[] {
                "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO", "CLIENTE", "GERENTE_GENERAL" }) {
            mockMvc.perform(post("/api/cotizaciones/8/respuesta-cliente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo)
                            .with(user("otro").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        verify(respuestas, org.mockito.Mockito.times(1)).registrar(anyLong(), any());
    }

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @Import(Controlador.class)
    static class SeguridadMinima {

        @Bean
        SecurityFilterChain cadena(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Configuration
    static class Controlador {

        @Bean
        CotizacionService cotizacionService() {
            return mock(CotizacionService.class);
        }

        @Bean
        RespuestaClienteService respuestaClienteService() {
            return mock(RespuestaClienteService.class);
        }

        @Bean
        CotizacionController cotizacionController(
                CotizacionService cotizacionService, RespuestaClienteService respuestaClienteService) {
            return new CotizacionController(cotizacionService, respuestaClienteService);
        }
    }
}
