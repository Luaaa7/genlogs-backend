package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

import com.genlogs.app.controller.MetaController;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.repository.AccionCorrectivaRepository;
import com.genlogs.app.repository.MetaPeriodoRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.CalculoPlazo.PlazoDeCotizacion;
import com.genlogs.app.service.CalculoPlazo.ResultadoPlazo;

@SpringJUnitConfig(MetasPeriodoTest.SeguridadMinima.class)
@WebAppConfiguration
class MetasPeriodoTest {

    private static final BigDecimal META = new BigDecimal("90");
    private static final LocalDate LIMITE = LocalDate.of(2026, 11, 10);

    @Autowired private WebApplicationContext contexto;
    @Autowired private MetasPeriodoService servicioDelControlador;

    private MockMvc mockMvc;

    @BeforeEach
    void mvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        reset(servicioDelControlador);
    }

    @Test
    void nueveDeDiezConMetaNoventaCumple() {
        ResultadoPlazo resultado = CalculoPlazo.calcular(muestra(9, 1), META);

        assertEquals(9, resultado.enPlazo());
        assertEquals(10, resultado.consideradas());
        assertEquals(0, resultado.porcentaje().compareTo(new BigDecimal("90.00")));
        assertTrue(resultado.cumple());
    }

    @Test
    void ochoDeDiezNoCumple() {
        ResultadoPlazo resultado = CalculoPlazo.calcular(muestra(8, 2), META);

        assertEquals(8, resultado.enPlazo());
        assertEquals(10, resultado.consideradas());
        assertEquals(0, resultado.porcentaje().compareTo(new BigDecimal("80.00")));
        assertFalse(resultado.cumple());
    }

    @Test
    void ceroCotizacionesDejanElPorcentajeEnNull() {
        ResultadoPlazo resultado = CalculoPlazo.calcular(List.of(), META);

        assertEquals(0, resultado.consideradas());
        assertNull(resultado.porcentaje());
        assertNull(resultado.cumple());
    }

    @Test
    void unaPosteriorASuFechaLimiteCuentaComoFuera() {
        ResultadoPlazo resultado = CalculoPlazo.calcular(
                List.of(new PlazoDeCotizacion(LIMITE.plusDays(1), LIMITE)), META);

        assertEquals(0, resultado.enPlazo());
        assertEquals(1, resultado.consideradas());
        assertEquals(0, resultado.porcentaje().compareTo(new BigDecimal("0.00")));
    }

    @Test
    void unaSinFechaLimiteNoSeConsidera() {
        ResultadoPlazo resultado = CalculoPlazo.calcular(List.of(
                new PlazoDeCotizacion(LIMITE, LIMITE),
                new PlazoDeCotizacion(LIMITE, null)), META);

        assertEquals(1, resultado.enPlazo());
        assertEquals(1, resultado.consideradas());
    }

    @Test
    void laDescripcionVaciaSeRechaza() {
        MetasPeriodoService servicio = new MetasPeriodoService(
                mock(MetaPeriodoRepository.class),
                mock(AccionCorrectivaRepository.class),
                mock(SolicitudCotizacionRepository.class),
                mock(UsuarioRepository.class));

        BusinessException vacia = assertThrows(BusinessException.class,
                () -> servicio.registrarAccion(2026, 11, "   "));
        assertEquals("La descripción de la acción no puede ir vacía", vacia.getMessage());
    }

    @Test
    void quienNoEsJefeNoPuedeCrearLaAccion() throws Exception {
        mockMvc.perform(post("/api/metas/2026/11/acciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Revisar el plazo\"}")
                        .with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verify(servicioDelControlador, never()).registrarAccion(anyInt(), anyInt(), any());
    }

    private static List<PlazoDeCotizacion> muestra(int enPlazo, int fuera) {
        List<PlazoDeCotizacion> cotizaciones = new ArrayList<>();
        for (int i = 0; i < enPlazo; i++) {
            cotizaciones.add(new PlazoDeCotizacion(LIMITE, LIMITE));
        }
        for (int i = 0; i < fuera; i++) {
            cotizaciones.add(new PlazoDeCotizacion(LIMITE.plusDays(1), LIMITE));
        }
        return cotizaciones;
    }

    /** Solo el controlador y la seguridad de método. Sin base de datos ni el filtro JWT. */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @Import(MetasPeriodoTest.Controlador.class)
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
        MetasPeriodoService metasPeriodoService() {
            return mock(MetasPeriodoService.class);
        }

        @Bean
        MetaController metaController(MetasPeriodoService metasPeriodoService) {
            return new MetaController(metasPeriodoService);
        }
    }
}
