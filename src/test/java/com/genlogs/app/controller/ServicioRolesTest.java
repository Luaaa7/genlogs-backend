package com.genlogs.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import com.genlogs.app.model.Servicio;
import com.genlogs.app.service.ServicioService;

/**
 * Quién puede registrar, editar, desactivar, aprobar y rechazar servicios (@PreAuthorize).
 * Solo prueba la seguridad de método; el filtro de URL de SecurityConfig queda fuera.
 */
@SpringJUnitConfig(ServicioRolesTest.SeguridadMinima.class)
@WebAppConfiguration
class ServicioRolesTest {

    private static final String[] PUEDEN_REGISTRAR = {"JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"};
    private static final String[] NO_PUEDEN_REGISTRAR = {
            "GERENTE_GENERAL", "CONTADOR_GENERAL", "SUPERVISOR_SST", "ASISTENTE_ADMINISTRATIVO", "CLIENTE",
            "ADMINISTRADOR", "VENDEDOR"};

    @Autowired private WebApplicationContext contexto;
    @Autowired private ServicioService servicioService;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        reset(servicioService);
        when(servicioService.registrar(any())).thenReturn(new Servicio());
        when(servicioService.actualizar(any(), any())).thenReturn(new Servicio());
        when(servicioService.rechazar(any())).thenReturn(new Servicio());
        when(servicioService.buscarPorId(7L)).thenReturn(ServicioCostoVisibleTest.servicio());
    }

    /** S1: el Asistente Administrativo ve el servicio, pero sin costo, margen ni precio manual. */
    @Test
    void sinPermisoLaRespuestaNoTraeCostoNiMargen() throws Exception {
        for (String rol : new String[] {"ASISTENTE_ADMINISTRATIVO"}) {
            mockMvc.perform(get("/api/servicios/7").with(user("u").roles(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.precioReferencial").value(1250))
                    .andExpect(jsonPath("$.costo").doesNotExist())
                    .andExpect(jsonPath("$.margen").doesNotExist())
                    .andExpect(jsonPath("$.precioManual").doesNotExist());
        }
    }

    /** S2: Contador, Supervisor SST y Cliente no ven servicios. */
    @Test
    void contadorSstYClienteNoVenServicios() throws Exception {
        for (String rol : new String[] {"CONTADOR_GENERAL", "SUPERVISOR_SST", "CLIENTE"}) {
            mockMvc.perform(get("/api/servicios/7").with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/servicios").with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void conPermisoLaRespuestaTraeCostoYMargen() throws Exception {
        for (String rol : new String[] {"GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"}) {
            mockMvc.perform(get("/api/servicios/7").with(user("u").roles(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.costo").value(1000))
                    .andExpect(jsonPath("$.margen").value(25))
                    .andExpect(jsonPath("$.categoriaServicio.categoriaPadre.idCategoriaServicio").value(1))
                    .andExpect(jsonPath("$.unidadMedida.idUnidadMedida").value(3));
        }
    }

    @Test
    void elJefeYElAsistenteLogisticoRegistranEditanYDesactivan() throws Exception {
        for (String rol : PUEDEN_REGISTRAR) {
            mockMvc.perform(post("/api/servicios").with(user("u").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isCreated());
            mockMvc.perform(put("/api/servicios/7").with(user("u").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/servicios/7").with(user("u").roles(rol)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void losDemasRolesNoRegistranEditanNiDesactivan() throws Exception {
        for (String rol : NO_PUEDEN_REGISTRAR) {
            mockMvc.perform(post("/api/servicios").with(user("u").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/servicios/7").with(user("u").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/servicios/7").with(user("u").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        verify(servicioService, never()).registrar(any());
        verify(servicioService, never()).actualizar(any(), any());
        verify(servicioService, never()).desactivar(any());
    }

    @Test
    void soloElJefeRechaza() throws Exception {
        mockMvc.perform(post("/api/servicios/7/rechazar").with(user("asistente").roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isForbidden());
        verify(servicioService, never()).rechazar(any());

        mockMvc.perform(post("/api/servicios/7/rechazar").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        verify(servicioService).rechazar(7L);
    }

    @Test
    void elAsistenteLogisticoNoAprueba() throws Exception {
        mockMvc.perform(post("/api/servicios/7/aprobar").with(user("asistente").roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isForbidden());
        verify(servicioService, never()).aprobar(any());
    }

    /** Solo el controlador y la seguridad de método. Sin base de datos ni el filtro JWT. */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @Import(ServicioRolesTest.Controlador.class)
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
        ServicioService servicioService() {
            return mock(ServicioService.class);
        }

        @Bean
        ServicioController servicioController(ServicioService servicioService) {
            return new ServicioController(servicioService);
        }
    }
}