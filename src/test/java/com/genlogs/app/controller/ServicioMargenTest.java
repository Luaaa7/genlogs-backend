package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.genlogs.app.enums.EstadoAprobacionServicio;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.CategoriaServicioRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.service.ServicioService;

@SpringJUnitConfig(ServicioMargenTest.SeguridadMinima.class)
@WebAppConfiguration
class ServicioMargenTest {

    @Autowired private WebApplicationContext contexto;
    @Autowired private ServicioService servicioDelControlador;

    private MockMvc mockMvc;
    private ServicioRepository servicios;
    private ServicioService servicio;

    @BeforeEach
    void datos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        reset(servicioDelControlador);
        servicios = mock(ServicioRepository.class);
        CategoriaServicioRepository categorias = mock(CategoriaServicioRepository.class);
        UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
        ParametrosService parametros = mock(ParametrosService.class);
        when(parametros.getDecimal(ClavesParametro.MARGEN_SERV_MIN)).thenReturn(new BigDecimal("15"));
        when(parametros.getDecimal(ClavesParametro.MARGEN_SERV_MAX)).thenReturn(new BigDecimal("45"));
        when(servicios.existsByCodigoServicioIgnoreCase(any())).thenReturn(false);
        when(servicios.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        CategoriaServicio categoria = new CategoriaServicio();
        categoria.setIdCategoriaServicio(1);
        when(categorias.findById(1)).thenReturn(Optional.of(categoria));
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        when(unidades.findById(1)).thenReturn(Optional.of(unidad));
        servicio = new ServicioService(servicios, categorias, unidades, parametros);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "x", List.of()));
    }

    @AfterEach
    void limpia() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void costoCienYMargenTreintaYCincoDejanPrecioCientoTreintaYCinco() {
        Servicio guardado = servicio.registrar(datos(new BigDecimal("100"), new BigDecimal("35"), false, null));

        assertEquals(0, guardado.getPrecioReferencial().compareTo(new BigDecimal("135.00")));
        assertEquals(EstadoAprobacionServicio.APROBADO, guardado.getEstadoAprobacion());
    }

    @Test
    void conPrecioManualElPrecioQuedaElEnviado() {
        Servicio guardado = servicio.registrar(
                datos(new BigDecimal("100"), new BigDecimal("35"), true, new BigDecimal("200.00")));

        assertEquals(0, guardado.getPrecioReferencial().compareTo(new BigDecimal("200.00")));
    }

    @Test
    void margenDiezYCincuentaQuedanPendientesYQuinceYCuarentaYCincoAprobados() {
        assertEquals(EstadoAprobacionServicio.PENDIENTE_APROBACION,
                servicio.registrar(datos(new BigDecimal("100"), new BigDecimal("10"), false, null)).getEstadoAprobacion());
        assertEquals(EstadoAprobacionServicio.PENDIENTE_APROBACION,
                servicio.registrar(datos(new BigDecimal("100"), new BigDecimal("50"), false, null)).getEstadoAprobacion());
        assertEquals(EstadoAprobacionServicio.APROBADO,
                servicio.registrar(datos(new BigDecimal("100"), new BigDecimal("15"), false, null)).getEstadoAprobacion());
        assertEquals(EstadoAprobacionServicio.APROBADO,
                servicio.registrar(datos(new BigDecimal("100"), new BigDecimal("45"), false, null)).getEstadoAprobacion());
    }

    @Test
    void elJefeApruebaUnoPendiente() {
        Servicio pendiente = datos(new BigDecimal("100"), new BigDecimal("10"), false, null);
        pendiente.setIdServicio(7L);
        pendiente.setEstadoAprobacion(EstadoAprobacionServicio.PENDIENTE_APROBACION);
        when(servicios.findById(7L)).thenReturn(Optional.of(pendiente));

        Servicio aprobado = servicio.aprobar(7L);

        assertEquals(EstadoAprobacionServicio.APROBADO, aprobado.getEstadoAprobacion());
    }

    @Test
    void quienNoEsJefeNoPuedeAprobar() throws Exception {
        mockMvc.perform(post("/api/servicios/7/aprobar").with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verify(servicioDelControlador, never()).aprobar(any());
    }

    @Test
    void elJefePasaElFiltroDelMetodo() throws Exception {
        when(servicioDelControlador.aprobar(7L)).thenReturn(new Servicio());

        mockMvc.perform(post("/api/servicios/7/aprobar").with(user("jefe").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk());

        verify(servicioDelControlador).aprobar(7L);
    }

    private static Servicio datos(BigDecimal costo, BigDecimal margen, Boolean manual, BigDecimal precio) {
        CategoriaServicio categoria = new CategoriaServicio();
        categoria.setIdCategoriaServicio(1);
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        Servicio servicio = new Servicio();
        servicio.setCodigoServicio("SRV-1");
        servicio.setNombreServicio("Mantenimiento");
        servicio.setCategoriaServicio(categoria);
        servicio.setUnidadMedida(unidad);
        servicio.setCosto(costo);
        servicio.setMargen(margen);
        servicio.setPrecioManual(manual);
        servicio.setPrecioReferencial(precio);
        return servicio;
    }

    /** Solo el controlador y la seguridad de método. Sin base de datos ni el filtro JWT. */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    @Import(ServicioMargenTest.Controlador.class)
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
