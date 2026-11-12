package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;
import com.genlogs.app.sourcing.dto.ResultadoBusquedaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private FuenteBusqueda ebay;

    @BeforeEach
    void setUp() {
        historialRepo = mock(HistorialSourcingRepository.class);
        monedaRepo = mock(MonedaRepository.class);
        ebay = mock(FuenteBusqueda.class);
        when(ebay.codigo()).thenReturn("EBAY");
        when(historialRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepo.findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                any(), any(), any())).thenReturn(List.of());
    }

    /** Crea el servicio con las fuentes indicadas, igual que Spring con ObjectProvider. */
    private SourcingService servicio(FuenteBusqueda... fs) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        for (int i = 0; i < fs.length; i++) {
            bf.registerSingleton("fuente" + i, fs[i]);
        }
        SourcingService s = new SourcingService(historialRepo, mock(SolicitudItemRepository.class),
                monedaRepo, mock(UsuarioRepository.class), bf.getBeanProvider(FuenteBusqueda.class));
        s.setClock(Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneId.of("America/Lima")));
        return s;
    }

    private static BuscarReferenciasRequest pedido(String numeroParte, boolean forzar) {
        return new BuscarReferenciasRequest(null, numeroParte, "Caterpillar", null, null, forzar);
    }

    @Test
    void busquedaRepetidaEn24h_noConsultaLaFuente() {
        HistorialSourcing previo = HistorialSourcing.builder()
                .idSourcing(1L).fuente("EBAY").metodo(MetodoBusqueda.NUMERO_PARTE)
                .numeroParte("1R-0750").estado(EstadoSourcing.UTIL)
                .creadoEn(LocalDateTime.of(2026, 11, 11, 8, 0)).build();
        when(historialRepo.findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                eq("1R-0750"), eq(MetodoBusqueda.URL), any())).thenReturn(List.of(previo));

        ResultadoBusquedaResponse r = servicio(ebay).buscar(pedido(" 1R-0750 ", false));

        assertThat(r.reutilizada()).isTrue();
        assertThat(r.referencias()).hasSize(1);
        verify(ebay, never()).buscar(any());
    }

    @Test
    void forzar_consultaAunqueHayaBusquedaReciente() {
        when(ebay.buscar(any())).thenReturn(List.of());
        servicio(ebay).buscar(pedido("1R-0750", true));
        verify(ebay).buscar(any());
        verify(historialRepo, never())
                .findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(any(), any(), any());
    }

    @Test
    void sinFuentesConfiguradas_funcionaYSugierePegarUrl() {
        ResultadoBusquedaResponse r = servicio().buscar(pedido("1R-0750", false));

        assertThat(r.reutilizada()).isFalse();
        assertThat(r.referencias()).isEmpty();
        assertThat(r.sugerirUrl()).isTrue();
    }

    @Test
    void resultadosSeGuardanComoCapturadoConSuMoneda() {
        Moneda usd = Moneda.builder().idMoneda(2).codigoMoneda("USD").build();
        when(monedaRepo.findByCodigoMoneda("USD")).thenReturn(Optional.of(usd));
        when(ebay.buscar(any())).thenReturn(List.of(new ResultadoBusqueda(
                "Filtro de aceite CAT 1R-0750", "Caterpillar", "1R-0750",
                new BigDecimal("18.50"), "usd", "https://www.ebay.com/itm/1", "https://i.ebayimg.com/1.jpg")));

        ResultadoBusquedaResponse r = servicio(ebay).buscar(pedido("1R-0750", false));

        assertThat(r.fuentesConsultadas()).containsExactly("EBAY");
        assertThat(r.referencias()).singleElement().satisfies(ref -> {
            assertThat(ref.estado()).isEqualTo(EstadoSourcing.CAPTURADO);
            assertThat(ref.codigoMoneda()).isEqualTo("USD");
            assertThat(ref.precioRef()).isEqualByComparingTo("18.50");
        });
    }

    @Test
    void unaFuenteQueFalla_noDetieneLaBusqueda() {
        FuenteBusqueda ml = mock(FuenteBusqueda.class);
        when(ml.codigo()).thenReturn("MERCADO_LIBRE");
        when(ml.buscar(any())).thenThrow(new IllegalStateException("cuota agotada"));
        when(ebay.buscar(any())).thenReturn(List.of(new ResultadoBusqueda(
                "Filtro", null, null, BigDecimal.TEN, null, "https://www.ebay.com/itm/2", null)));

        ResultadoBusquedaResponse r = servicio(ml, ebay).buscar(pedido("1R-0750", false));

        assertThat(r.fuentesConError()).containsExactly("MERCADO_LIBRE");
        assertThat(r.fuentesConsultadas()).containsExactly("EBAY");
        assertThat(r.referencias()).hasSize(1);
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
}
