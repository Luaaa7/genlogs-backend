package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.sourcing.dto.GuardarUrlRequest;
import com.genlogs.app.sourcing.dto.ReferenciaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CapturaUrlServiceTest {

    private HistorialSourcingRepository historialRepo;
    private MonedaRepository monedaRepo;
    private CapturaUrlService servicio;

    @BeforeEach
    void setUp() {
        historialRepo = mock(HistorialSourcingRepository.class);
        monedaRepo = mock(MonedaRepository.class);
        when(historialRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(monedaRepo.findByCodigoMoneda("USD"))
                .thenReturn(Optional.of(Moneda.builder().idMoneda(2).codigoMoneda("USD").build()));
        servicio = new CapturaUrlService(mock(LectorUrl.class), historialRepo,
                mock(SolicitudItemRepository.class), monedaRepo, mock(UsuarioRepository.class));
    }

    private static GuardarUrlRequest pedido(String moneda, String tipoCambio, boolean editado) {
        return new GuardarUrlRequest(null, "https://www.amazon.com/dp/B000TEST", "Filtro CAT 1R-0750",
                "Caterpillar", "1R-0750", new BigDecimal("100"), moneda, "https://m.media-amazon.com/x.jpg",
                "IMPORTADO", tipoCambio == null ? null : new BigDecimal(tipoCambio),
                new BigDecimal("50"), new BigDecimal("10"), new BigDecimal("20"), editado);
    }

    @Test
    void guardaComoUrlConFuenteDetectadaYCostoPuestoEnLima() {
        ReferenciaResponse r = servicio.guardar(pedido("usd", "3.75", true));

        ArgumentCaptor<HistorialSourcing> captor = ArgumentCaptor.forClass(HistorialSourcing.class);
        verify(historialRepo).save(captor.capture());
        HistorialSourcing h = captor.getValue();
        assertThat(h.getMetodo()).isEqualTo(MetodoBusqueda.URL);
        assertThat(h.getFuente()).isEqualTo("AMAZON");
        assertThat(h.getCostoPuestoLima()).isEqualByComparingTo("455.00");
        assertThat(h.getTipoCambioUsado()).isEqualByComparingTo("3.75");
        assertThat(h.getOrigen()).isEqualTo("IMPORTADO");
        assertThat(h.getEditadoAMano()).isTrue();
        assertThat(r.estado()).isEqualTo(EstadoSourcing.CAPTURADO);
    }

    @Test
    void monedaNoRegistrada_esRechazada() {
        when(monedaRepo.findByCodigoMoneda("EUR")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.guardar(pedido("EUR", "4.10", false)))
                .isInstanceOf(BusinessException.class);
        verify(historialRepo, never()).save(any());
    }

    @Test
    void dolaresSinTipoCambio_noSeGuarda() {
        assertThatThrownBy(() -> servicio.guardar(pedido("USD", null, false)))
                .isInstanceOf(BusinessException.class);
        verify(historialRepo, never()).save(any());
    }

    @Test
    void imagenQueNoEsHttp_esRechazada() {
        GuardarUrlRequest p = pedido("USD", "3.75", false);
        GuardarUrlRequest conImagenMala = new GuardarUrlRequest(p.idSolicitudItem(), p.url(), p.titulo(), p.marca(),
                p.numeroParte(), p.precio(), p.codigoMoneda(), "javascript:alert(1)", p.origen(), p.tipoCambio(),
                p.flete(), p.seguro(), p.aduana(), p.editadoAMano());

        assertThatThrownBy(() -> servicio.guardar(conImagenMala)).isInstanceOf(BusinessException.class);
        verify(historialRepo, never()).save(any());
    }

    @Test
    void urlEnOtroPuerto_esRechazada() {
        GuardarUrlRequest p = pedido("USD", "3.75", false);
        GuardarUrlRequest otroPuerto = new GuardarUrlRequest(p.idSolicitudItem(), "http://tienda.com:6379/x", p.titulo(),
                p.marca(), p.numeroParte(), p.precio(), p.codigoMoneda(), p.urlImagen(), p.origen(), p.tipoCambio(),
                p.flete(), p.seguro(), p.aduana(), p.editadoAMano());

        assertThatThrownBy(() -> servicio.guardar(otroPuerto)).isInstanceOf(BusinessException.class);
        verify(historialRepo, never()).save(any());
    }
}
