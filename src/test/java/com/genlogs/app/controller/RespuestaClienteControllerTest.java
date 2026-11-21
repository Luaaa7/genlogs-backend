package com.genlogs.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.GlobalExceptionHandler;
import com.genlogs.app.service.CotizacionService;
import com.genlogs.app.service.RespuestaClienteService;

class RespuestaClienteControllerTest {

    private static final String CUERPO = """
            {"codigoEstado":"ACEPTADA_CLIENTE","comentario":"Llamó el 16/11 a las 9:00","idAdjunto":7}
            """;

    private CotizacionService cotizaciones;
    private RespuestaClienteService respuestas;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cotizaciones = mock(CotizacionService.class);
        respuestas = mock(RespuestaClienteService.class);
        when(cotizaciones.buscarPorId(15L)).thenReturn(
                CotizacionResponse.builder().idCotizacion(15L).estadoCodigo("ACEPTADA_CLIENTE").build());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CotizacionController(cotizaciones, respuestas))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void aceptarYRechazarRegistranLaRespuestaSinCrearLaOrden() throws Exception {
        when(respuestas.registrar(eq(15L), any())).thenReturn("ACEPTADA_CLIENTE");
        mockMvc.perform(post("/api/cotizaciones/15/respuesta-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk());

        when(respuestas.registrar(eq(15L), any()))
                .thenThrow(new BusinessException("Esta cotización ya no se puede aprobar ni rechazar"));
        mockMvc.perform(post("/api/cotizaciones/15/respuesta-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest());

        verify(respuestas, times(2)).registrar(eq(15L), any());
        // v3.4: aceptar ya no crea la orden; se registra después en el alta de la OC.
        verify(cotizaciones, never()).registrarOrdenSiAprobada(eq(15L), any());

        when(respuestas.registrar(eq(16L), any())).thenReturn("RECHAZADA");
        when(cotizaciones.buscarPorId(16L)).thenReturn(
                CotizacionResponse.builder().idCotizacion(16L).estadoCodigo("RECHAZADA").build());
        mockMvc.perform(post("/api/cotizaciones/16/respuesta-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoEstado":"RECHAZADA","comentario":"Correo del 16/11: no compran","idAdjunto":8}
                                """))
                .andExpect(status().isOk());

        verify(cotizaciones, never()).registrarOrdenSiAprobada(eq(16L), any());
    }

    @Test
    void marcarEnviadaInvalidaResponde409YLaNuevaRespuestaNoCreaLaOrden() throws Exception {
        doThrow(new EstadoNoPermitidoException(
                "Solo se marca como enviada una cotización aprobada por el Jefe. Ahora está en Borrador."))
                .when(respuestas).marcarEnviada(15L);
        mockMvc.perform(post("/api/cotizaciones/15/marcar-enviada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Solo se marca como enviada una cotización aprobada por el Jefe. Ahora está en Borrador."));

        when(respuestas.registrarRespuesta(eq(15L), eq("aceptó"), eq("2026-11-16"), eq("correo"), eq("ok"), isNull()))
                .thenReturn(com.genlogs.app.dto.SeguimientoCotizacionResponse.builder()
                        .estadoNuevo("ACEPTADA_CLIENTE")
                        .estadoAnterior("ENVIADA")
                        .build());
        mockMvc.perform(post("/api/cotizaciones/15/respuesta-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accion":"aceptó","fecha_respuesta":"2026-11-16","medio":"correo","nota":"ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seguimiento.estadoNuevo").value("ACEPTADA_CLIENTE"))
                .andExpect(jsonPath("$.cotizacion.idCotizacion").value(15));

        verify(cotizaciones, never()).registrarOrdenSiAprobada(eq(15L), any());
    }
}