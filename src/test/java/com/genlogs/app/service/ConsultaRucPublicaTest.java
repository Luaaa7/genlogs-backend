package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.genlogs.app.exception.DemasiadasSolicitudesException;
import com.genlogs.app.model.ConsultaDocumento;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.ConsultaDocumentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.TerceroRepository;

class ConsultaRucPublicaTest {

    private ConsultaDocumentoRepository consultas;
    private TerceroRepository terceros;
    private FactilizaService servicio;

    @BeforeEach
    void setUp() {
        consultas = mock(ConsultaDocumentoRepository.class);
        terceros = mock(TerceroRepository.class);
        servicio = new FactilizaService(mock(DistritoRepository.class), terceros, consultas, mock(UbigeoService.class));
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
}
