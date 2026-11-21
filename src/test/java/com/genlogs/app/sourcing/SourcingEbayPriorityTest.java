package com.genlogs.app.sourcing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;

class SourcingEbayPriorityTest {

    private HistorialSourcingRepository historialRepo;
    private FuenteBusquedaConfigRepository fuenteRepo;
    private ConsultaFuenteService consultaFuente;
    private FuenteBusquedaConfig ebay;

    @BeforeEach
    void setUp() {
        historialRepo = mock(HistorialSourcingRepository.class);
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        consultaFuente = mock(ConsultaFuenteService.class);
        ebay = fuente(true);
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of(ebay));
        when(historialRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    private SourcingService servicio(FuenteBusqueda... apis) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        for (int i = 0; i < apis.length; i++) {
            bf.registerSingleton("api" + i, apis[i]);
        }
        return new SourcingService(historialRepo, mock(SolicitudItemRepository.class),
                mock(MonedaRepository.class), mock(UsuarioRepository.class), fuenteRepo,
                consultaFuente, mock(ParametrosService.class),
                bf.getBeanProvider(FuenteBusqueda.class));
    }

    private static FuenteBusquedaConfig fuente(boolean activa) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(CodigoFuente.EBAY);
        f.setNombre("EBAY");
        f.setUrlBase("https://example.com");
        f.setActiva(activa);
        f.setLimiteDiario(50);
        f.setPausaSegundos(0);
        return f;
    }

    private static BuscarReferenciasRequest pedido() {
        return new BuscarReferenciasRequest(null, "1R-0750", null, null, null, true);
    }

    @Test
    void ebayConApiNoConsultaElLector() {
        FuenteBusqueda api = mock(FuenteBusqueda.class);
        when(api.codigo()).thenReturn("EBAY");
        when(api.buscar(any())).thenReturn(List.of());

        servicio(api).buscar(pedido());

        verify(consultaFuente, never()).consultar(any(), any());
        verify(api).buscar(any());
    }

    @Test
    void ebaySinApiConsultaElLector() {
        when(consultaFuente.consultar(eq(ebay), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.SIN_RESULTADOS, null, List.of()));

        servicio().buscar(pedido());

        verify(consultaFuente).consultar(eq(ebay), any());
    }

    @Test
    void ebayInactivoNoEjecutaNingunaImplementacion() {
        ebay.setActiva(false);
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(List.of());
        FuenteBusqueda api = mock(FuenteBusqueda.class);
        when(api.codigo()).thenReturn("EBAY");

        servicio(api).buscar(pedido());

        verify(consultaFuente, never()).consultar(any(), any());
        verify(api, never()).buscar(any());
    }
}
