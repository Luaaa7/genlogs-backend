package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;
import com.genlogs.app.sourcing.dto.ResultadoBusquedaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourcingServiceTest {

    private HistorialSourcingRepository historialRepo;
    private MonedaRepository monedaRepo;
    private FuenteBusquedaConfigRepository fuenteRepo;
    private ConsultaFuenteService consultaFuente;
    private ParametrosService parametros;
    private FuenteBusquedaConfig ebay;

    @BeforeEach
    void setUp() {
        historialRepo = mock(HistorialSourcingRepository.class);
        monedaRepo = mock(MonedaRepository.class);
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        consultaFuente = mock(ConsultaFuenteService.class);
        parametros = mock(ParametrosService.class);
        when(historialRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepo.findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                any(), any(), any())).thenReturn(List.of());
        when(parametros.buscar(ClavesParametro.BUSQUEDA_REUSO_DIAS)).thenReturn(Optional.empty());
        ebay = fuente(CodigoFuente.EBAY, true);
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of(ebay));
    }

    private SourcingService servicio(FuenteBusqueda... apis) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        for (int i = 0; i < apis.length; i++) {
            bf.registerSingleton("api" + i, apis[i]);
        }
        SourcingService s = new SourcingService(historialRepo, mock(SolicitudItemRepository.class),
                monedaRepo, mock(UsuarioRepository.class), fuenteRepo, consultaFuente, parametros,
                bf.getBeanProvider(FuenteBusqueda.class));
        s.setClock(Clock.fixed(Instant.parse("2026-11-11T20:00:00Z"), ZoneId.of("America/Lima")));
        return s;
    }

    private static BuscarReferenciasRequest pedido(String numeroParte, boolean forzar) {
        return new BuscarReferenciasRequest(null, numeroParte, "Caterpillar", null, null, forzar);
    }

    private static FuenteBusquedaConfig fuente(CodigoFuente codigo, boolean activa) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(codigo);
        f.setNombre(codigo.name());
        f.setUrlBase("https://example.com");
        f.setActiva(activa);
        f.setPausaSegundos(0);
        f.setLimiteDiario(50);
        f.setUserCreate("PRUEBA");
        f.setProcessCreate("PRUEBA");
        return f;
    }

    @Test
    void busquedaDeHaceTresDias_seReutiliza() {
        HistorialSourcing previo = HistorialSourcing.builder()
                .idSourcing(1L).fuente("EBAY").metodo(MetodoBusqueda.NUMERO_PARTE)
                .numeroParte("1R-0750").estado(EstadoSourcing.UTIL)
                .creadoEn(LocalDateTime.of(2026, 11, 8, 8, 0)).build();
        when(historialRepo.findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                eq("1R-0750"), eq(MetodoBusqueda.URL), any())).thenReturn(List.of(previo));

        ResultadoBusquedaResponse r = servicio().buscar(pedido(" 1R-0750 ", false));

        assertThat(r.reutilizada()).isTrue();
        assertThat(r.referencias()).hasSize(1);
        verify(consultaFuente, never()).consultar(any(), any());
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(historialRepo).findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                eq("1R-0750"), eq(MetodoBusqueda.URL), desde.capture());
        assertThat(desde.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 4, 15, 0));
    }

    @Test
    void forzar_consultaAunqueHayaBusquedaReciente() {
        when(consultaFuente.consultar(any(), any())).thenReturn(
                ConsultaFuente.de("EBAY", EstadoConsultaFuente.SIN_RESULTADOS, null, List.of()));
        servicio().buscar(pedido("1R-0750", true));
        verify(consultaFuente).consultar(eq(ebay), any());
        verify(historialRepo, never())
                .findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(any(), any(), any());
    }

    @Test
    void sinFuentesActivas_funcionaYSugierePegarUrl() {
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of());
        ResultadoBusquedaResponse r = servicio().buscar(pedido("1R-0750", false));

        assertThat(r.reutilizada()).isFalse();
        assertThat(r.referencias()).isEmpty();
        assertThat(r.sugerirUrl()).isTrue();
        verify(consultaFuente, never()).consultar(any(), any());
    }

    @Test
    void resultadosSeGuardanComoCapturadoConSuMoneda() {
        Moneda usd = Moneda.builder().idMoneda(2).codigoMoneda("USD").build();
        when(monedaRepo.findByCodigoMoneda("USD")).thenReturn(Optional.of(usd));
        when(consultaFuente.consultar(eq(ebay), any())).thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.OK, null,
                List.of(new ResultadoBusqueda(
                        "Filtro de aceite CAT 1R-0750", "Caterpillar", "1R-0750",
                        new BigDecimal("18.50"), "usd", "https://www.ebay.com/itm/1", "https://i.ebayimg.com/1.jpg"))));

        ResultadoBusquedaResponse r = servicio().buscar(pedido("1R-0750", false));

        assertThat(r.fuentesConsultadas()).containsExactly("EBAY");
        assertThat(r.referencias()).singleElement().satisfies(ref -> {
            assertThat(ref.estado()).isEqualTo(EstadoSourcing.CAPTURADO);
            assertThat(ref.codigoMoneda()).isEqualTo("USD");
            assertThat(ref.precioRef()).isEqualByComparingTo("18.50");
        });
    }

    @Test
    void unaFuenteQueFalla_noDetieneLaBusqueda() {
        FuenteBusquedaConfig ml = fuente(CodigoFuente.MERCADO_LIBRE, true);
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of(ml, ebay));
        when(consultaFuente.consultar(eq(ml), any()))
                .thenReturn(ConsultaFuente.de("MERCADO_LIBRE", EstadoConsultaFuente.CON_ERROR, "caída", List.of()));
        when(consultaFuente.consultar(eq(ebay), any())).thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.OK, null,
                List.of(new ResultadoBusqueda(
                        "Filtro", null, null, BigDecimal.TEN, null, "https://www.ebay.com/itm/2", null))));

        ResultadoBusquedaResponse r = servicio().buscar(pedido("1R-0750", false));

        assertThat(r.fuentesConError()).containsExactly("MERCADO_LIBRE");
        assertThat(r.fuentesConsultadas()).containsExactly("MERCADO_LIBRE", "EBAY");
        assertThat(r.referencias()).hasSize(1);
    }

    @Test
    void consultarLanzaExcepcion_seRegistraComoErrorYSigue() {
        FuenteBusquedaConfig ml = fuente(CodigoFuente.MERCADO_LIBRE, true);
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of(ml, ebay));
        when(consultaFuente.consultar(eq(ml), any())).thenThrow(new RuntimeException("boom"));
        when(consultaFuente.consultar(eq(ebay), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.SIN_RESULTADOS, null, List.of()));

        ResultadoBusquedaResponse r = servicio().buscar(pedido("1R-0750", false));

        assertThat(r.fuentesConError()).containsExactly("MERCADO_LIBRE");
        assertThat(r.fuentesSinResultados()).containsExactly("EBAY");
        assertThat(r.fuentesConsultadas()).containsExactly("EBAY");
    }

    @Test
    void estadosDeOmision_sePartenPorTipo() {
        FuenteBusquedaConfig ml = fuente(CodigoFuente.MERCADO_LIBRE, true);
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of(ml, ebay));
        when(consultaFuente.consultar(eq(ml), any()))
                .thenReturn(ConsultaFuente.de("MERCADO_LIBRE", EstadoConsultaFuente.OMITIDA_SESION, "sin sesión", List.of()));
        when(consultaFuente.consultar(eq(ebay), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.BLOQUEADA, "403", List.of()));

        ResultadoBusquedaResponse r = servicio().buscar(pedido("1R-0750", false));

        assertThat(r.fuentesOmitidas()).containsExactly("MERCADO_LIBRE");
        assertThat(r.fuentesBloqueadas()).containsExactly("EBAY");
        assertThat(r.fuentesConError()).isEmpty();
    }

    @Test
    void fuenteApiQueFalla_seRegistraComoErrorYNoDetieneLaBusqueda() {
        when(consultaFuente.consultar(eq(ebay), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.NO_CONECTADO, "P2", List.of()));
        FuenteBusqueda api = mock(FuenteBusqueda.class);
        when(api.codigo()).thenReturn("EBAY");
        when(api.buscar(any())).thenThrow(new RuntimeException("cuota diaria agotada"));

        ResultadoBusquedaResponse r = servicio(api).buscar(pedido("1R-0750", true));

        assertThat(r.fuentesConError()).containsExactly("EBAY");
        assertThat(r.referencias()).isEmpty();
        assertThat(r.sugerirUrl()).isTrue();
    }

    @Test
    void numeroParteVacio_esRechazado() {
        assertThatThrownBy(() -> servicio().buscar(pedido("   ", false)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void referenciaConvertida_noCambiaDeEstado() {
        when(historialRepo.findById(5L)).thenReturn(Optional.of(
                HistorialSourcing.builder().idSourcing(5L).estado(EstadoSourcing.CONVERTIDO).build()));

        assertThatThrownBy(() -> servicio().cambiarEstado(5L, EstadoSourcing.DESCARTADO))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noSePuedeMarcarConvertidoAMano() {
        assertThatThrownBy(() -> servicio().cambiarEstado(5L, EstadoSourcing.CONVERTIDO))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void marcarUtil_guardaElNuevoEstado() {
        HistorialSourcing h = HistorialSourcing.builder().idSourcing(7L).estado(EstadoSourcing.CAPTURADO).build();
        when(historialRepo.findById(7L)).thenReturn(Optional.of(h));
        when(historialRepo.save(h)).thenReturn(h);

        assertThat(servicio().cambiarEstado(7L, EstadoSourcing.UTIL).estado()).isEqualTo(EstadoSourcing.UTIL);
    }

    @Test
    void apiEbay_seUsaSiElScrapingNoLeyoLaTienda() {
        when(consultaFuente.consultar(eq(ebay), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.NO_CONECTADO, "P2", List.of()));
        FuenteBusqueda api = mock(FuenteBusqueda.class);
        when(api.codigo()).thenReturn("EBAY");
        when(api.buscar(any())).thenReturn(List.of(new ResultadoBusqueda(
                "Filtro API", null, "1R-0750", new BigDecimal("9.00"), "USD",
                "https://www.ebay.com/itm/api", null)));

        ResultadoBusquedaResponse r = servicio(api).buscar(pedido("1R-0750", true));

        verify(api).buscar(any());
        assertThat(r.referencias()).hasSize(1);
        assertThat(r.referencias().get(0).titulo()).isEqualTo("Filtro API");
        assertThat(r.fuentesConsultadas()).contains("EBAY");
        assertThat(r.fuentesSinLector()).isEmpty();
    }

    @Test
    void apiEbay_noDuplicaSiEl9b1YaLeyo() {
        when(consultaFuente.consultar(eq(ebay), any())).thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.OK,
                null, List.of(new ResultadoBusqueda("Scraping", null, null, BigDecimal.ONE, "USD",
                        "https://www.ebay.com/itm/s", null))));
        FuenteBusqueda api = mock(FuenteBusqueda.class);
        when(api.codigo()).thenReturn("EBAY");

        servicio(api).buscar(pedido("1R-0750", true));

        verify(api, never()).buscar(any());
    }
}