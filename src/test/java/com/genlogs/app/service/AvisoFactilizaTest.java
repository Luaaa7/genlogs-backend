package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.repository.ConsultaDocumentoRepository;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * BPMN 10-F · Si Factiliza rechaza la clave (plan vencido, sin consultas o token inválido),
 * se avisa al Jefe Administrativo una vez al día, con correo. Antes solo se veía
 * «No se pudo actualizar el tipo de cambio», sin la causa (22/11).
 */
class AvisoFactilizaTest {


    private NotificacionService notificaciones;
    private NotificacionRepository avisosGuardados;
    private HttpServer servidor;
    private final AtomicReference<Integer> estado = new AtomicReference<>(401);

    @BeforeEach
    void setUp() throws IOException {
        notificaciones = mock(NotificacionService.class);
        avisosGuardados = mock(NotificacionRepository.class);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            byte[] cuerpo = "{\"status\":401,\"message\":\"Plan vencido\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado.get(), cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1";
    }

    private static Clock dia(String fechaHora) {
        return Clock.fixed(Instant.parse(fechaHora), ZoneOffset.UTC);
    }

    private AvisoFactiliza aviso(String fechaHora) {
        return new AvisoFactiliza(notificaciones, avisosGuardados, dia(fechaHora));
    }

    @Test
    void claveRechazadaAvisaAlJefeConCorreoYLoLlevaAlTipoDeCambio() {
        aviso("2026-11-22T13:00:00Z").revisar(401);

        verify(notificaciones, times(1)).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.FACTILIZA_SIN_PLAN),
                contains("Factiliza"), contains("equipo técnico"), eq("/configuracion/tipo-cambio"), eq(true));
    }

    @Test
    void siYaSeAvisoHoySegunLaTablaNoRepiteAunqueElBackendSeReinicie() {
        when(avisosGuardados.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                eq("FACTILIZA_SIN_PLAN"), eq("/configuracion/tipo-cambio"), any(), any())).thenReturn(true);

        aviso("2026-11-22T13:00:00Z").revisar(403);

        verify(notificaciones, never()).avisarRol(any(), any(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void elDiaQueSeRevisaEsElDeLimaNoElDeUtc() {
        // 03:00 UTC del 23 todavía es el 22 en Lima.
        aviso("2026-11-23T03:00:00Z").revisar(402);

        verify(avisosGuardados).existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                "FACTILIZA_SIN_PLAN", "/configuracion/tipo-cambio",
                LocalDateTime.of(2026, 11, 22, 0, 0), LocalDateTime.of(2026, 11, 23, 0, 0));
    }

    @Test
    void el429EsLimiteDeConsultasPorMinutoYNoAvisaPlanVencido() {
        aviso("2026-11-22T13:00:00Z").revisar(429);

        verify(notificaciones, never()).avisarRol(any(), any(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void elAvisoNuncaMuestraLaClaveDeFactiliza() {
        ClienteFactilizaTipoCambio cliente = new ClienteFactilizaTipoCambio("clave-secreta-123", urlBase());
        cliente.setAvisoFactiliza(aviso("2026-11-22T13:00:00Z"));

        assertThatThrownBy(() -> cliente.ventaDelDia(java.time.LocalDate.of(2026, 11, 22)))
                .isInstanceOf(FactilizaSinPlanException.class);

        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).avisarRol(any(), any(), titulo.capture(), mensaje.capture(), anyString(), anyBoolean());
        assertThat(titulo.getValue()).doesNotContain("clave-secreta-123");
        assertThat(mensaje.getValue()).doesNotContain("clave-secreta-123");
    }

    @Test
    void otrosErroresNoSonDelPlanYNoAvisan() {
        AvisoFactiliza aviso = aviso("2026-11-22T13:00:00Z");

        aviso.revisar(200);
        aviso.revisar(404);
        aviso.revisar(500);
        aviso.revisar(503);

        verify(notificaciones, never()).avisarRol(any(), any(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void siFallaElAvisoNoRompeLaConsulta() {
        doThrow(new RuntimeException("sin base")).when(notificaciones)
                .avisarRol(any(), any(), anyString(), anyString(), anyString(), anyBoolean());
        aviso("2026-11-22T13:00:00Z").revisar(401);
    }

    @Test
    void elTipoDeCambioAvisaCuandoFactilizaRechazaLaClave() {
        AvisoFactiliza aviso = mock(AvisoFactiliza.class);
        ClienteFactilizaTipoCambio cliente = new ClienteFactilizaTipoCambio("token", urlBase());
        cliente.setAvisoFactiliza(aviso);

        assertThatThrownBy(() -> cliente.ventaDelDia(java.time.LocalDate.of(2026, 11, 22)))
                .isInstanceOf(FactilizaSinPlanException.class);
        verify(aviso).revisar(401);
    }

    @Test
    void laConsultaDeRucAvisaCuandoFactilizaRechazaLaClave() {
        estado.set(403);
        AvisoFactiliza aviso = mock(AvisoFactiliza.class);
        ConsultaDocumentoRepository consultas = mock(ConsultaDocumentoRepository.class);
        TerceroRepository terceros = mock(TerceroRepository.class);
        org.mockito.Mockito.when(terceros.buscarRegistrado(anyString(), anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.when(consultas.findByNumeroDocumento(anyString())).thenReturn(Optional.empty());
        FactilizaService servicio = new FactilizaService(mock(DistritoRepository.class), terceros, consultas, mock(UbigeoService.class));
        ReflectionTestUtils.setField(servicio, "apiKey", "token");
        ReflectionTestUtils.setField(servicio, "baseUrl", urlBase());
        servicio.setAvisoFactiliza(aviso);

        assertThatThrownBy(() -> servicio.consultarPorNumero("20123456789")).isInstanceOf(BusinessException.class);
        verify(aviso).revisar(403);
        assertThat(servicio.consultarRazonSocialPublica("20123456789")).isNull();
    }
}
