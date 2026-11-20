package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sun.net.httpserver.HttpServer;

import com.genlogs.app.controller.ConsultaRucPublicaController;
import com.genlogs.app.exception.GlobalExceptionHandler;

import com.genlogs.app.exception.DemasiadasSolicitudesException;
import com.genlogs.app.model.ConsultaDocumento;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.ConsultaDocumentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.TerceroRepository;

class ConsultaRucPublicaTest {

    private ConsultaDocumentoRepository consultas;
    private TerceroRepository terceros;
    private UbigeoService ubigeo;
    private FactilizaService servicio;

    @BeforeEach
    void setUp() {
        consultas = mock(ConsultaDocumentoRepository.class);
        terceros = mock(TerceroRepository.class);
        ubigeo = mock(UbigeoService.class);
        when(ubigeo.buscarIdDistrito(nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Optional.empty());
        servicio = new FactilizaService(mock(DistritoRepository.class), terceros, consultas, ubigeo);
        ReflectionTestUtils.setField(servicio, "apiKey", "");
    }

    @Test
    void sinLlaveNiTerceroNiCacheRespondeSinDatoYNuncaConsultaDni() {
        when(terceros.buscarRegistrado("RUC", "20123456789")).thenReturn(Optional.empty());
        when(consultas.findByNumeroDocumento("20123456789")).thenReturn(Optional.empty());

        assertThat(servicio.consultarRazonSocialPublica("20123456789")).isNull();

        verify(terceros).buscarRegistrado("RUC", "20123456789");
        verify(terceros, never()).buscarRegistrado(org.mockito.ArgumentMatchers.eq("DNI"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void elTerceroVaAntesQueLaCacheYSoloDevuelveLaRazonSocial() {
        Tercero tercero = Tercero.builder()
                .numeroDocumento("20123456789")
                .razonSocial("COMPAÑIA YA REGISTRADA S.A.C.")
                .direccion("Av. Interna 9")
                .build();
        when(terceros.buscarRegistrado("RUC", "20123456789")).thenReturn(Optional.of(tercero));

        assertThat(servicio.consultarRazonSocialPublica("20123456789")).isEqualTo("COMPAÑIA YA REGISTRADA S.A.C.");
        verify(consultas, never()).findByNumeroDocumento(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void laCacheDevuelveSoloLaRazonSocial() {
        when(terceros.buscarRegistrado("RUC", "20123456789")).thenReturn(Optional.empty());
        ConsultaDocumento guardado = ConsultaDocumento.builder()
                .numeroDocumento("20123456789")
                .tipoDocumento("RUC")
                .razonSocial("MINERA REAL S.A.C.")
                .direccion("Av. Secreta 123")
                .ubigeo("150101")
                .vecesConsultado(1)
                .build();
        when(consultas.findByNumeroDocumento("20123456789")).thenReturn(Optional.of(guardado));
        when(consultas.save(guardado)).thenReturn(guardado);

        assertThat(servicio.consultarRazonSocialPublica("20123456789")).isEqualTo("MINERA REAL S.A.C.");
    }

    @Test
    void elLogDeFactilizaDejaSoloLosTresUltimosDigitosDelRuc() {
        assertThat(FactilizaService.enmascararDocumento("http://127.0.0.1/ruc/info/20123456789 body 20999888777"))
                .doesNotContain("20123456789")
                .doesNotContain("20999888777")
                .contains("***789")
                .contains("***777");
    }

    @Test
    void elLimitePorIpEsDiezConsultas() {
        LimitadorConsultaRuc limitador = new LimitadorConsultaRuc();
        for (int i = 0; i < LimitadorConsultaRuc.MAXIMO; i++) {
            limitador.verificar("203.0.113.5");
        }
        assertThatThrownBy(() -> limitador.verificar("203.0.113.5"))
                .isInstanceOf(DemasiadasSolicitudesException.class);
        limitador.verificar("203.0.113.9");
    }

    @Test
    void laIpSaleDelPrimerValorReenviadoSiTieneFormaDeIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.4, 10.0.0.8");
        assertThat(IpCliente.de(request)).isEqualTo("203.0.113.4");

        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "no-soy-una-ip, 203.0.113.4");
        assertThat(IpCliente.de(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void factilizaDevuelveLaRazonSocialYElGetLaEntrega() throws Exception {
        when(terceros.buscarRegistrado("RUC", "20123456789")).thenReturn(Optional.empty());
        when(consultas.findByNumeroDocumento("20123456789")).thenReturn(Optional.empty());
        when(consultas.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        HttpServer server = factiliza("20123456789", "MINERA FACTILIZA S.A.C.");
        try {
            mockMvc().perform(get("/api/public/ruc/20123456789"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.razonSocial").value("MINERA FACTILIZA S.A.C."));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cacheConRazonEnBlancoSigueAFactiliza() throws Exception {
        when(terceros.buscarRegistrado("RUC", "20123456789")).thenReturn(Optional.empty());
        ConsultaDocumento vacio = ConsultaDocumento.builder()
                .numeroDocumento("20123456789")
                .tipoDocumento("RUC")
                .razonSocial("   ")
                .vecesConsultado(1)
                .build();
        when(consultas.findByNumeroDocumento("20123456789")).thenReturn(Optional.of(vacio));
        when(consultas.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        HttpServer server = factiliza("20123456789", "MINERA DESDE FACTILIZA S.A.C.");
        try {
            assertThat(servicio.consultarRazonSocialPublica("20123456789"))
                    .isEqualTo("MINERA DESDE FACTILIZA S.A.C.");
        } finally {
            server.stop(0);
        }
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new ConsultaRucPublicaController(servicio, new LimitadorConsultaRuc()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private HttpServer factiliza(String ruc, String razon) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] cuerpo = ("{\"data\":{\"nombre_o_razon_social\":\"" + razon + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
        server.createContext("/ruc/info/" + ruc, intercambio -> {
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        server.start();
        ReflectionTestUtils.setField(servicio, "apiKey", "llave-de-prueba");
        ReflectionTestUtils.setField(servicio, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        return server;
    }
}
