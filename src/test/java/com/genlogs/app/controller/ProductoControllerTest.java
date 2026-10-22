package com.genlogs.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.genlogs.app.dto.PageResponseDto;
import com.genlogs.app.dto.ProductoResponse;
import com.genlogs.app.service.ProductoService;

/**
 * Contrato HTTP del catálogo de repuestos: la respuesta paginada y el paso de
 * filtros. El orden global por código y los metadatos se validan en
 * ProductoIntegracionTest (contra la base de datos) y en ApiEndToEndTest.
 */
class ProductoControllerTest {

    private MockMvc mockMvc;
    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = mock(ProductoService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductoController(productoService)).build();
    }

    @Test
    void listar_porDefectoPide12PorPagina() throws Exception {
        when(productoService.listarPaginado(isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(paginaVacia());

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12));

        verify(productoService).listarPaginado(null, null, null, null, 0, 12);
    }

    @Test
    void listar_pasaLosFiltrosAlServicio() throws Exception {
        when(productoService.listarPaginado(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(paginaVacia());

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "placa")
                        .param("codigo", "GLB-000")
                        .param("idCategoriaProducto", "3")
                        .param("idMarca", "7")
                        .param("page", "2")
                        .param("size", "12"))
                .andExpect(status().isOk());

        verify(productoService).listarPaginado(eq("placa"), eq("GLB-000"), eq(3), eq(7), eq(2), eq(12));
    }

    @Test
    void listar_devuelveLosMetadatosDePaginacion() throws Exception {
        when(productoService.listarPaginado(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponseDto<>(List.of(), 31, 3, 1, 12));

        mockMvc.perform(get("/api/productos?page=1&size=12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(31))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(12));
    }

    private PageResponseDto<ProductoResponse> paginaVacia() {
        return new PageResponseDto<>(List.of(), 0, 0, 0, 12);
    }
}
