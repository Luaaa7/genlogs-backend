package com.genlogs.app.sourcing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.fuentes.config.LectorFuenteNoConectado;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapturaUrlConSesionTest {

    private LectorUrl lectorUrl;
    private FuenteBusquedaConfigRepository fuenteRepo;
    private ConsultaFuenteService consultaFuente;
    private CapturaUrlService servicio;
    private FuenteBusquedaConfig ebay;

    @BeforeEach
    void setUp() {
        lectorUrl = mock(LectorUrl.class);
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        consultaFuente = mock(ConsultaFuenteService.class);
        servicio = new CapturaUrlService(lectorUrl, mock(HistorialSourcingRepository.class),
                mock(SolicitudItemRepository.class), mock(MonedaRepository.class), mock(UsuarioRepository.class),
                fuenteRepo, consultaFuente);
        ebay = new FuenteBusquedaConfig();
        ebay.setCodigo(CodigoFuente.EBAY);
        ebay.setActiva(true);
        ebay.setEstado(EstadoFuente.ACTIVA);
        ebay.setNombre("eBay");
        when(fuenteRepo.findByCodigo(CodigoFuente.EBAY)).thenReturn(Optional.of(ebay));
    }

    @Test
    void urlDeFuenteActivaConSesion_seLeeConP2() {
        when(consultaFuente.consultarUrl(eq(ebay), eq("https://www.ebay.com/itm/1"), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.OK, null, List.of(
                        new ResultadoBusqueda("Filtro", null, null, new BigDecimal("18.50"), "USD",
                                "https://www.ebay.com/itm/1", "https://i.ebayimg.com/1.jpg"))));

        LectorUrl.DatosUrl d = servicio.leer("https://www.ebay.com/itm/1");

        assertThat(d.titulo()).isEqualTo("Filtro");
        assertThat(d.bloqueada()).isFalse();
        verify(lectorUrl, never()).leer(any());
    }

    @Test
    void sinSesion_usaLectorUrlSinCambiarEstado() {
        when(consultaFuente.consultarUrl(eq(ebay), any(), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.OMITIDA_SESION, "sin sesión", List.of()));
        when(lectorUrl.leer("https://www.ebay.com/itm/1")).thenReturn(
                new LectorUrl.DatosUrl("https://www.ebay.com/itm/1", "EBAY", "x", null, null, null, false, null));

        servicio.leer("https://www.ebay.com/itm/1");

        verify(lectorUrl).leer("https://www.ebay.com/itm/1");
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
    }

    @Test
    void stubP2_usaLectorUrlSinMarcarError() {
        when(consultaFuente.consultarUrl(eq(ebay), any(), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.NO_CONECTADO,
                        LectorFuenteNoConectado.MENSAJE, List.of()));
        when(lectorUrl.leer("https://www.ebay.com/itm/1")).thenReturn(
                new LectorUrl.DatosUrl("https://www.ebay.com/itm/1", "EBAY", "meta", null, null, null, false, null));

        LectorUrl.DatosUrl d = servicio.leer("https://www.ebay.com/itm/1");

        assertThat(d.titulo()).isEqualTo("meta");
        verify(lectorUrl).leer("https://www.ebay.com/itm/1");
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
    }

    @Test
    void errorRealDeLaTienda_noHaceFallback() {
        when(consultaFuente.consultarUrl(eq(ebay), any(), any()))
                .thenReturn(ConsultaFuente.de("EBAY", EstadoConsultaFuente.CON_ERROR, "timeout", List.of()));

        LectorUrl.DatosUrl d = servicio.leer("https://www.ebay.com/itm/1");

        assertThat(d.bloqueada()).isTrue();
        assertThat(d.motivo()).contains("timeout");
        verify(lectorUrl, never()).leer(any());
    }

    @Test
    void hostDesconocido_usaLectorUrl() {
        when(lectorUrl.leer("https://proveedor.example/p")).thenReturn(
                new LectorUrl.DatosUrl("https://proveedor.example/p", "PROVEEDOR", "p", null, null, null, false, null));

        servicio.leer("https://proveedor.example/p");

        verify(consultaFuente, never()).consultarUrl(any(), any(), any());
        verify(lectorUrl).leer("https://proveedor.example/p");
    }

    @Test
    void fuenteInactiva_usaLectorUrlSinSesion() {
        ebay.setActiva(false);
        when(lectorUrl.leer("https://www.ebay.com/itm/1")).thenReturn(
                new LectorUrl.DatosUrl("https://www.ebay.com/itm/1", "EBAY", "x", null, null, null, false, null));

        servicio.leer("https://www.ebay.com/itm/1");

        verify(consultaFuente, never()).consultarUrl(any(), any(), any());
        verify(lectorUrl).leer("https://www.ebay.com/itm/1");
    }

    @Test
    void urlInterna_esRechazadaPorSsrf() {
        CapturaUrlService conLectorReal = new CapturaUrlService(new LectorUrl(),
                mock(HistorialSourcingRepository.class), mock(SolicitudItemRepository.class),
                mock(MonedaRepository.class), mock(UsuarioRepository.class), fuenteRepo, consultaFuente);
        assertThatThrownBy(() -> conLectorReal.leer("http://127.0.0.1/admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("internas");
        verify(consultaFuente, never()).consultarUrl(any(), any(), any());
    }

    @Test
    void urlEnOtroPuerto_esRechazada() {
        assertThatThrownBy(() -> servicio.leer("https://www.ebay.com:6379/itm/1"))
                .isInstanceOf(BusinessException.class);
        verify(consultaFuente, never()).consultarUrl(any(), any(), any());
    }
}
