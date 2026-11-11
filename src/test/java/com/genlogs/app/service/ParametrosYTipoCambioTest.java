package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.enums.FuenteTipoCambio;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.ParametroSistema;
import com.genlogs.app.model.TipoCambio;
import com.genlogs.app.repository.ParametroSistemaRepository;
import com.genlogs.app.repository.TipoCambioRepository;

/** Paso 0 - lectura de parametros (RF-87) y del tipo de cambio del dia (RF-86). */
class ParametrosYTipoCambioTest {

    private static ParametroSistema parametro(String clave, String valor) {
        ParametroSistema p = new ParametroSistema();
        p.setClave(clave);
        p.setValor(valor);
        return p;
    }

    @Test
    void parametros_leeEnteroYDecimal() {
        ParametroSistemaRepository repo = mock(ParametroSistemaRepository.class);
        when(repo.findById(ClavesParametro.PLAZO_COMUN_DIAS)).thenReturn(Optional.of(parametro("PLAZO_COMUN_DIAS", "1")));
        when(repo.findById(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .thenReturn(Optional.of(parametro("MONTO_ALTO_VALOR_PEN", " 20000 ")));
        ParametrosService servicio = new ParametrosService(repo);

        assertThat(servicio.getEntero(ClavesParametro.PLAZO_COMUN_DIAS)).isEqualTo(1);
        assertThat(servicio.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN)).isEqualByComparingTo("20000");
    }

    @Test
    void parametros_siFalta_errorClaro() {
        ParametroSistemaRepository repo = mock(ParametroSistemaRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());
        ParametrosService servicio = new ParametrosService(repo);

        assertThatThrownBy(() -> servicio.get("NO_EXISTE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NO_EXISTE");
        assertThat(servicio.buscar("NO_EXISTE")).isEmpty();
    }

    @Test
    void parametros_valorNoNumerico_errorClaro() {
        ParametroSistemaRepository repo = mock(ParametroSistemaRepository.class);
        when(repo.findById("X")).thenReturn(Optional.of(parametro("X", "abc")));
        ParametrosService servicio = new ParametrosService(repo);

        assertThatThrownBy(() -> servicio.getEntero("X")).isInstanceOf(BusinessException.class);
    }

    private static TipoCambio tipoCambio(LocalDate fecha, String valor) {
        TipoCambio t = new TipoCambio();
        t.setFecha(fecha);
        t.setValor(new BigDecimal(valor));
        t.setFuente(FuenteTipoCambio.SUNAT);
        return t;
    }

    /** 11/11/2026 10:00 en Lima. */
    private static Clock elOnceDeNoviembre() {
        return Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneId.of("America/Lima"));
    }

    @Test
    void tipoCambio_deHoy() {
        TipoCambioRepository repo = mock(TipoCambioRepository.class);
        LocalDate hoy = LocalDate.of(2026, 11, 11);
        when(repo.findFirstByMoneda_CodigoMonedaAndFechaLessThanEqualOrderByFechaDesc("USD", hoy))
                .thenReturn(Optional.of(tipoCambio(hoy, "3.7520")));
        TipoCambioService servicio = new TipoCambioService(repo);
        servicio.setClock(elOnceDeNoviembre());

        TipoCambioDia dia = servicio.delDia().orElseThrow();

        assertThat(dia.valor()).isEqualByComparingTo("3.7520");
        assertThat(dia.codigoMoneda()).isEqualTo("USD");
        assertThat(dia.esDeHoy()).isTrue();
    }

    @Test
    void tipoCambio_siHoyNoHay_usaElUltimoYLoIndica() {
        TipoCambioRepository repo = mock(TipoCambioRepository.class);
        when(repo.findFirstByMoneda_CodigoMonedaAndFechaLessThanEqualOrderByFechaDesc(eq("USD"), any()))
                .thenReturn(Optional.of(tipoCambio(LocalDate.of(2026, 11, 10), "3.7400")));
        TipoCambioService servicio = new TipoCambioService(repo);
        servicio.setClock(elOnceDeNoviembre());

        TipoCambioDia dia = servicio.delDia().orElseThrow();

        assertThat(dia.esDeHoy()).isFalse();
        assertThat(dia.fecha()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void tipoCambio_tablaVacia_devuelveVacio() {
        TipoCambioRepository repo = mock(TipoCambioRepository.class);
        when(repo.findFirstByMoneda_CodigoMonedaAndFechaLessThanEqualOrderByFechaDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThat(new TipoCambioService(repo).delDia()).isEmpty();
    }
}