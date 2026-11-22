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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.repository.ConsultaDocumentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * BPMN 10-F · Si Factiliza rechaza la clave (plan vencido, sin consultas o token inválido),
 * se avisa al Jefe Administrativo una vez al día, con correo. Antes solo se veía
 * «No se pudo actualizar el tipo de cambio», sin la causa (22/11).
 */
class AvisoFactilizaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private NotificacionService notificaciones;
    private HttpServer servidor;
    private final AtomicReference<Integer> estado = new AtomicReference<>(401);

    @BeforeEach
    void setUp() throws IOException {
        notificaciones = mock(NotificacionService.class);
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

    @Test
    void claveRechazadaAvisaAlJefeConCorreoUnaVezPorDia() {
        AvisoFactiliza aviso = new AvisoFactiliza(notificaciones, dia("2026-11-22T13:00:00Z"));

        aviso.revisar(401);
        aviso.revisar(403);
        aviso.revisar(402);

        verify(notificaciones, times(1)).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.FACTILIZA_SIN_PLAN),
                contains("Factiliza"), contains("FACTILIZA_API_KEY"), eq("/tipo-cambio"), eq(true));
    }

    @Test
    void alDiaSiguienteVuelveAAvisar() {
        AtomicReference<Clock> reloj = new AtomicReference<>(dia("2026-11-22T13:00:00Z"));
        AvisoFactiliza aviso = new AvisoFactiliza(notificaciones, null) {
            @Override
            java.time.LocalDate hoy() {
                return java.time.LocalDate.now(reloj.get().withZone(LIMA));
            }
        };
        aviso.revisar(401);
        reloj.set(dia("2026-11-23T13:00:00Z"));
        aviso.revisar(401);

        verify(notificaciones, times(2)).avisarRol(any(), eq(TipoAviso.FACTILIZA_SIN_PLAN), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void otrosErroresNoSonDelPlanYNoAvisan() {
        AvisoFactiliza aviso = new AvisoFactiliza(notificaciones, dia("2026-11-22T13:00:00Z"));

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
        AvisoFactiliza aviso = new AvisoFactiliza(notificaciones, dia("2026-11-22T13:00:00Z"));

        aviso.revisar(401);
    }

    @Test
    void elTipoDeCambioAvisaCuandoFactilizaRechazaLaClave() {
        AvisoFactiliza aviso = mock(AvisoFactiliza.class);
        ClienteFactilizaTipoCambio cliente = new ClienteFactilizaTipoCambio("token", urlBase());
        cliente.setAvisoFactiliza(aviso);

        assertThatThrownBy(() -> cliente.ventaDelDia(java.time.LocalDate.of(2026, 11, 22)))
                .isInstanceOf(IllegalStateException.class);
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
