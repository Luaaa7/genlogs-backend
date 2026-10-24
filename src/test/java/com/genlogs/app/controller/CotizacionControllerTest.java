package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.exception.GlobalExceptionHandler;
import com.genlogs.app.service.CotizacionService;

/**
 * El listado de cotizaciones TOMABA el sortBy del frontend sin validarlo y lo
 * inyectaba tal cual en el ORDER BY del JPQL de CotizacionRepository.
 * Un campo que no existe en la entidad Cotizacion (por ejemplo "fechaCreacion")
 * provocaba "Could not resolve attribute ..." y la pantalla respondía HTTP 500.
 *
 * Estos tests fijan el contrato: los alias legados se aceptan, y cualquier
 * campo o dirección no válido devuelve 400 en lugar de 500.
 */
class CotizacionControllerTest {

    private CotizacionService cotizacionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cotizacionService = mock(CotizacionService.class);
        PaginaResponse<CotizacionResponse> paginaVacia =
                PaginaResponse.of(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(cotizacionService.listar(any(), any(), any(), any()))
                .thenReturn(paginaVacia);

        mockMvc = MockMvcBuilders.standaloneSetup(new CotizacionController(cotizacionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listar_aliasLegadoFechaCreacion_ordenaPorFechaCotizacion() throws Exception {
        mockMvc.perform(get("/api/cotizaciones")
                        .param("sortBy", "fechaCreacion")
                        .param("sortDir", "DESC"))
                .andExpect(status().isOk());

        Sort.Order orden = pageableCapturado().getSort().getOrderFor("fechaCotizacion");
        assertEquals("fechaCotizacion", orden.getProperty());
        assertEquals(Sort.Direction.DESC, orden.getDirection());
    }

    @Test
    void listar_conCampoValido_ordenaPorEseCampo() throws Exception {
        mockMvc.perform(get("/api/cotizaciones")
                        .param("sortBy", "codigoCotizacion")
                        .param("sortDir", "ASC"))
                .andExpect(status().isOk());

        Sort.Order orden = pageableCapturado().getSort().getOrderFor("codigoCotizacion");
        assertEquals("codigoCotizacion", orden.getProperty());
        assertEquals(Sort.Direction.ASC, orden.getDirection());
    }

    @Test
    void listar_conCampoInexistente_devuelve400() throws Exception {
        mockMvc.perform(get("/api/cotizaciones").param("sortBy", "campoInventado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Campo de ordenamiento inválido")));
    }

    /** "total" se calcula en vw_cotizacion_totales: la tabla no tiene esa columna. */
    @Test
    void listar_conTotal_devuelve400() throws Exception {
        mockMvc.perform(get("/api/cotizaciones").param("sortBy", "total"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_conDireccionInvalida_devuelve400() throws Exception {
        mockMvc.perform(get("/api/cotizaciones")
                        .param("sortBy", "fechaCotizacion")
                        .param("sortDir", "NO_VALIDA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Dirección de ordenamiento inválida")));
    }

    @Test
    void listar_conPaginacionInvalida_noRompe() throws Exception {
        mockMvc.perform(get("/api/cotizaciones")
                        .param("page", "-1")
                        .param("size", "0"))
                .andExpect(status().isOk());

        Pageable pageable = pageableCapturado();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(1, pageable.getPageSize());
    }

    private Pageable pageableCapturado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(cotizacionService).listar(any(), any(), any(), captor.capture());
        return captor.getValue();
    }
}
