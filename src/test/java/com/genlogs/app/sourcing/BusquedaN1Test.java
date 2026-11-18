package com.genlogs.app.sourcing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FichaFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.LectorFuente;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SesionFuenteService;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;
import com.genlogs.app.sourcing.dto.ResultadoBusquedaResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N1 · Tres fuentes simuladas (A activa, B inactiva, C caída) sin tiendas reales ni GENLOGS_FUENTES_CLAVE.
 */
class BusquedaN1Test {

    private FuenteBusquedaConfigRepository fuenteRepo;
    private SesionFuenteService sesiones;
    private LectorFuente lector;
    private PausaFuente pausa;
    private HistorialSourcingRepository historialRepo;
    private SourcingService sourcing;
    private FuenteBusquedaConfig a;
    private FuenteBusquedaConfig b;
    private FuenteBusquedaConfig c;

    @BeforeEach
    void setUp() {
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        FuenteSelectorRepository selectorRepo = mock(FuenteSelectorRepository.class);
        sesiones = mock(SesionFuenteService.class);
        lector = mock(LectorFuente.class);
        pausa = mock(PausaFuente.class);
        historialRepo = mock(HistorialSourcingRepository.class);
        AvisoFuenteConError avisos = mock(AvisoFuenteConError.class);
        ParametrosService parametros = mock(ParametrosService.class);

        when(selectorRepo.findByFuente_CodigoAndStatus(any(), eq("A"))).thenReturn(List.of());
        when(historialRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepo.findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                any(), any(), any())).thenReturn(List.of());
        when(parametros.buscar(ClavesParametro.BUSQUEDA_REUSO_DIAS)).thenReturn(Optional.empty());

        a = fuente(CodigoFuente.EBAY, true, EstadoFuente.ACTIVA, 50);
        b = fuente(CodigoFuente.AMAZON, false, EstadoFuente.ACTIVA, 50);
        c = fuente(CodigoFuente.MERCADO_LIBRE, true, EstadoFuente.ACTIVA, 50);

        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of(a, c));
        when(sesiones.sesionVigente(CodigoFuente.EBAY)).thenReturn(Optional.of("sesion-a"));
        when(sesiones.sesionVigente(CodigoFuente.MERCADO_LIBRE)).thenReturn(Optional.of("sesion-c"));
        when(lector.buscar(eq(CodigoFuente.EBAY), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.ok(List.of(
                        new FichaFuente("Filtro CAT", "desc", "18.50", "usd",
                                "https://img.example/1.jpg", "https://ebay.example/itm/1"))));
        when(lector.buscar(eq(CodigoFuente.MERCADO_LIBRE), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.error("tienda caída", false));
        when(lector.buscar(eq(CodigoFuente.AMAZON), any(), any(), any(), any()))
                .thenThrow(new AssertionError("La fuente inactiva no debe consultarse"));

        ConsultaFuenteService consulta = new ConsultaFuenteService(fuenteRepo, selectorRepo, sesiones, lector,
                pausa, avisos);
        sourcing = new SourcingService(historialRepo, mock(SolicitudItemRepository.class),
                mock(MonedaRepository.class), mock(UsuarioRepository.class), fuenteRepo, consulta, parametros,
                new DefaultListableBeanFactory().getBeanProvider(FuenteBusqueda.class));
    }

    private static FuenteBusquedaConfig fuente(CodigoFuente codigo, boolean activa, EstadoFuente estado, int limite) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(codigo);
        f.setNombre(codigo.name());
        f.setUrlBase("https://" + codigo.name().toLowerCase() + ".example");
        f.setActiva(activa);
        f.setEstado(estado);
        f.setLimiteDiario(limite);
        f.setPausaSegundos(2);
        f.setUserCreate("PRUEBA");
        f.setProcessCreate("PRUEBA");
        return f;
    }

    @Test
    void tresFuentes_consultaA_omiteB_errorEnC_noDetiene() {
        ResultadoBusquedaResponse r = sourcing.buscar(
                new BuscarReferenciasRequest(null, "1R-0750", "Caterpillar", null, null, false));

        assertThat(r.fuentesConsultadas()).containsExactly("EBAY", "MERCADO_LIBRE");
        assertThat(r.fuentesConsultadas()).doesNotContain("AMAZON");
        assertThat(r.fuentesConError()).containsExactly("MERCADO_LIBRE");
        assertThat(r.referencias()).hasSize(1);
        assertThat(r.referencias().get(0).titulo()).isEqualTo("Filtro CAT");
        assertThat(r.referencias().get(0).precioRef()).isEqualByComparingTo(new BigDecimal("18.50"));
        assertThat(r.referencias().get(0).estado()).isEqualTo(EstadoSourcing.CAPTURADO);
        assertThat(c.getEstado()).isEqualTo(EstadoFuente.CON_ERROR);
        assertThat(a.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        verify(lector, never()).buscar(eq(CodigoFuente.AMAZON), any(), any(), any(), any());
        verify(pausa, times(2)).esperar(2);
        verify(sesiones).sesionVigente(CodigoFuente.EBAY);
        verify(sesiones).sesionVigente(CodigoFuente.MERCADO_LIBRE);
    }

    @Test
    void diferenciaBloqueadaErrorYSinResultados() {
        when(lector.buscar(eq(CodigoFuente.EBAY), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.bloqueada("403"));
        when(lector.buscar(eq(CodigoFuente.MERCADO_LIBRE), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.sinResultados());

        ResultadoBusquedaResponse r = sourcing.buscar(
                new BuscarReferenciasRequest(null, "1R-0750", "Caterpillar", null, null, true));

        assertThat(r.fuentesBloqueadas()).containsExactly("EBAY");
        assertThat(r.fuentesSinResultados()).containsExactly("MERCADO_LIBRE");
        assertThat(r.fuentesConError()).isEmpty();
        assertThat(r.referencias()).isEmpty();
        assertThat(a.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        assertThat(c.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
    }
}
